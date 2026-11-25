
interface Env {
  DB: D1Database;
  XXXCORE_ADMIN_SECRET: string;
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
    },
  });
}

async function sha256(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);

  return Array.from(new Uint8Array(digest))
    .map(b => b.toString(16).padStart(2, "0"))
    .join("");
}

function randomToken(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));

  return Array.from(bytes)
    .map(b => b.toString(16).padStart(2, "0"))
    .join("");
}

function constantTimeEqual(a: string, b: string): boolean {
  const length = Math.max(a.length, b.length);
  let difference = a.length ^ b.length;

  for (let i = 0; i < length; i++) {
    difference |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  }

  return difference === 0;
}

async function hashPassword(password: string): Promise<string> {
  const encoder = new TextEncoder();
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iterations = 100000;

  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(password),
    "PBKDF2",
    false,
    ["deriveBits"]
  );

  const bits = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      salt,
      iterations,
      hash: "SHA-256",
    },
    key,
    256
  );

  const saltHex = Array.from(salt)
    .map(b => b.toString(16).padStart(2, "0"))
    .join("");

  const hashHex = Array.from(new Uint8Array(bits))
    .map(b => b.toString(16).padStart(2, "0"))
    .join("");

  return `pbkdf2$${iterations}$${saltHex}$${hashHex}`;
}

async function logAdminAction(
  env: Env,
  action: string,
  details: string
): Promise<void> {
  await env.DB.prepare(
    `INSERT INTO admin_logs (id, action, details, created_at)
     VALUES (?, ?, ?, ?)`
  )
    .bind(
      crypto.randomUUID(),
      action,
      details,
      Math.floor(Date.now() / 1000)
    )
    .run();
}

async function verifySession(
  request: Request,
  env: Env
): Promise<boolean> {
  const authorization = request.headers.get("Authorization");

  if (!authorization?.startsWith("Bearer ")) {
    return false;
  }

  const token = authorization.slice(7).trim();

  if (!/^[a-f0-9]{64}$/.test(token)) {
    return false;
  }

  const tokenHash = await sha256(token);
  const now = Math.floor(Date.now() / 1000);

  const session = await env.DB.prepare(
    `SELECT id FROM admin_sessions
     WHERE token_hash = ?
       AND revoked = 0
       AND expires_at > ?
     LIMIT 1`
  )
    .bind(tokenHash, now)
    .first();

  return session !== null;
}

async function verifyAdminAccess(
  request: Request,
  env: Env
): Promise<boolean> {
  if (await verifySession(request, env)) {
    return true;
  }

  // Compatibilité temporaire avec l'ancienne API administrateur.
  const authorization = request.headers.get("Authorization");

  return Boolean(
    env.XXXCORE_ADMIN_SECRET &&
    authorization &&
    constantTimeEqual(
      authorization,
      `Bearer ${env.XXXCORE_ADMIN_SECRET}`
    )
  );
}

async function requireAdmin(
  request: Request,
  env: Env
): Promise<Response | null> {
  if (!env.XXXCORE_ADMIN_SECRET) {
    return json({ error: "Admin secret not configured" }, 503);
  }

  try {
    if (!(await verifyAdminAccess(request, env))) {
      return json({ error: "Unauthorized" }, 403);
    }

    return null;
  } catch (error) {
    console.error("ADMIN_AUTH_ERROR:", error);
    return json({ error: "Authentication service unavailable" }, 503);
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/") {
      return json({
        app: "XXX-CORE-VPN",
        brand: "FRANKDEV BUILD",
        status: "online",
      });
    }

    if (request.method === "GET" && url.pathname === "/health") {
      try {
        await env.DB.prepare("SELECT 1").first();

        return json({
          status: "ok",
          database: "connected",
        });
      } catch (error) {
        console.error("HEALTH_CHECK_ERROR:", error);

        return json({
          status: "error",
          database: "unavailable",
        }, 503);
      }
    }

    // ADMIN LOGIN
    if (
      request.method === "POST" &&
      url.pathname === "/admin/login"
    ) {
      if (!env.XXXCORE_ADMIN_SECRET) {
        return json({ error: "Admin secret not configured" }, 503);
      }

      let body: { adminSecret?: string };

      try {
        body = await request.json();
      } catch {
        return json({ error: "Invalid JSON" }, 400);
      }

      const suppliedSecret = body.adminSecret;

      if (
        typeof suppliedSecret !== "string" ||
        !constantTimeEqual(suppliedSecret, env.XXXCORE_ADMIN_SECRET)
      ) {
        try {
          await logAdminAction(
            env,
            "LOGIN_FAILED",
            "Rejected administrator authentication"
          );
        } catch (error) {
          console.error("ADMIN_LOG_ERROR:", error);
        }

        return json({
          error: "ACCÈS REFUSÉ",
          message: "Veuillez Réessayer 🔄🔄🔄",
        }, 403);
      }

      try {
        const token = randomToken();
        const tokenHash = await sha256(token);
        const now = Math.floor(Date.now() / 1000);
        const expiresAt = now + (8 * 60 * 60);

        await env.DB.prepare(
          `INSERT INTO admin_sessions
           (id, token_hash, created_at, expires_at, revoked)
           VALUES (?, ?, ?, ?, 0)`
        )
          .bind(
            crypto.randomUUID(),
            tokenHash,
            now,
            expiresAt
          )
          .run();

        await logAdminAction(
          env,
          "LOGIN_SUCCESS",
          "Administrator session created"
        );

        return json({
          status: "authenticated",
          token,
          token_type: "Bearer",
          expires_at: expiresAt,
        });
      } catch (error) {
        console.error("ADMIN_LOGIN_ERROR:", error);

        return json({
          error: "Unable to create administrator session",
        }, 500);
      }
    }

    // CHECK ADMIN SESSION
    if (
      request.method === "GET" &&
      url.pathname === "/admin/me"
    ) {
      try {
        if (!(await verifySession(request, env))) {
          return json({
            error: "Session expired or invalid",
          }, 403);
        }

        return json({
          status: "authenticated",
          role: "admin",
          brand: "FRANKDEV BUILD",
        });
      } catch (error) {
        console.error("ADMIN_SESSION_ERROR:", error);

        return json({
          error: "Session verification failed",
        }, 500);
      }
    }

    // ADMIN LOGOUT
    if (
      request.method === "POST" &&
      url.pathname === "/admin/logout"
    ) {
      const authorization = request.headers.get("Authorization");

      if (!authorization?.startsWith("Bearer ")) {
        return json({ error: "Session required" }, 403);
      }

      const token = authorization.slice(7).trim();

      if (!/^[a-f0-9]{64}$/.test(token)) {
        return json({ error: "Invalid session" }, 403);
      }

      try {
        const tokenHash = await sha256(token);

        await env.DB.prepare(
          `UPDATE admin_sessions
           SET revoked = 1
           WHERE token_hash = ?`
        )
          .bind(tokenHash)
          .run();

        await logAdminAction(
          env,
          "LOGOUT",
          "Administrator session revoked"
        );

        return json({ status: "logged_out" });
      } catch (error) {
        console.error("ADMIN_LOGOUT_ERROR:", error);

        return json({ error: "Logout failed" }, 500);
      }
    }

    // ADMIN CLIENT MANAGEMENT
    if (
      url.pathname === "/admin/clients" &&
      (request.method === "GET" || request.method === "POST")
    ) {
      const authError = await requireAdmin(request, env);
      if (authError) return authError;

      if (request.method === "POST") {
        let body: {
          username?: string;
          password?: string;
        };

        try {
          body = await request.json();
        } catch {
          return json({ error: "Invalid JSON" }, 400);
        }

        const username = body.username?.trim().toLowerCase();
        const password = body.password;

        if (
          !username ||
          !/^[a-z0-9_.-]{3,32}$/.test(username)
        ) {
          return json({ error: "Invalid username" }, 400);
        }

        if (
          typeof password !== "string" ||
          password.length < 10 ||
          password.length > 128
        ) {
          return json({
            error: "Password must contain 10 to 128 characters",
          }, 400);
        }

        try {
          const id = crypto.randomUUID();
          const passwordHash = await hashPassword(password);
          const createdAt = Math.floor(Date.now() / 1000);

          await env.DB.prepare(
            `INSERT INTO clients
             (id, username, password_hash, active, created_at)
             VALUES (?, ?, ?, 1, ?)`
          )
            .bind(id, username, passwordHash, createdAt)
            .run();

          await logAdminAction(
            env,
            "CLIENT_CREATED",
            `Client created: ${username}`
          );

          return json({
            status: "created",
            client: {
              id,
              username,
              active: true,
              created_at: createdAt,
            },
          }, 201);
        } catch (error) {
          console.error("CLIENT_CREATE_ERROR:", error);

          return json({
            error: "Client creation failed. Check Worker logs.",
          }, 500);
        }
      }

      try {
        const result = await env.DB.prepare(
          `SELECT id, username, active, created_at
           FROM clients
           ORDER BY created_at DESC`
        ).all();

        return json({
          status: "ok",
          clients: result.results,
        });
      } catch (error) {
        console.error("CLIENT_LIST_ERROR:", error);

        return json({ error: "Database query failed" }, 500);
      }
    }

    // ADMIN ONLINE CLIENTS
    if (
      request.method === "GET" &&
      url.pathname === "/admin/clients/online"
    ) {
      const authError = await requireAdmin(request, env);
      if (authError) return authError;

      try {
        const now = Math.floor(Date.now() / 1000);
        const onlineSince = now - 90;

        const result = await env.DB.prepare(
          `SELECT
             s.id AS session_id,
             s.client_id,
             c.username,
             s.connected_at,
             s.last_seen_at
           FROM client_sessions s
           INNER JOIN clients c ON c.id = s.client_id
           WHERE s.revoked = 0
             AND s.last_seen_at >= ?
             AND c.active = 1
           ORDER BY s.last_seen_at DESC`
        )
          .bind(onlineSince)
          .all();

        return json({
          status: "ok",
          online_count: result.results.length,
          online_since: onlineSince,
          clients: result.results,
        });
      } catch (error) {
        console.error("ONLINE_CLIENTS_ERROR:", error);

        return json({
          error: "Unable to retrieve online clients",
        }, 500);
      }
    }

    // ADMIN DISCONNECT CLIENT SESSION
    const disconnectMatch = url.pathname.match(
      /^\/admin\/clients\/sessions\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\/disconnect$/i
    );

    if (
      request.method === "POST" &&
      disconnectMatch
    ) {
      const authError = await requireAdmin(request, env);
      if (authError) return authError;

      const sessionId = disconnectMatch[1];
      const now = Math.floor(Date.now() / 1000);

      try {
        const result = await env.DB.prepare(
          `UPDATE client_sessions
           SET revoked = 1,
               disconnected_at = ?
           WHERE id = ?
             AND revoked = 0`
        )
          .bind(now, sessionId)
          .run();

        if (result.meta.changes === 0) {
          return json({
            error: "Session not found or already disconnected",
          }, 404);
        }

        await logAdminAction(
          env,
          "CLIENT_SESSION_DISCONNECTED",
          `Client session disconnected: ${sessionId}`
        );

        return json({
          status: "disconnected",
          session_id: sessionId,
          disconnected_at: now,
        });
      } catch (error) {
        console.error("CLIENT_DISCONNECT_ERROR:", error);

        return json({
          error: "Unable to disconnect client session",
        }, 500);
      }
    }

    // ADMIN ENABLE / DISABLE CLIENT
    const clientStatusMatch = url.pathname.match(
      /^\/admin\/clients\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\/status$/i
    );

    if (
      request.method === "PATCH" &&
      clientStatusMatch
    ) {
      const authError = await requireAdmin(request, env);
      if (authError) return authError;

      let body: { active?: boolean };

      try {
        body = await request.json();
      } catch {
        return json({ error: "Invalid JSON" }, 400);
      }

      if (typeof body.active !== "boolean") {
        return json({
          error: "The active field must be true or false",
        }, 400);
      }

      const clientId = clientStatusMatch[1];
      const active = body.active;

      try {
        const result = await env.DB.prepare(
          `UPDATE clients
           SET active = ?
           WHERE id = ?`
        )
          .bind(active ? 1 : 0, clientId)
          .run();

        if (result.meta.changes === 0) {
          return json({ error: "Client not found" }, 404);
        }

        await logAdminAction(
          env,
          active ? "CLIENT_ENABLED" : "CLIENT_DISABLED",
          `Client ${clientId} ${active ? "enabled" : "disabled"}`
        );

        return json({
          status: "updated",
          client: {
            id: clientId,
            active,
          },
        });
      } catch (error) {
        console.error("CLIENT_STATUS_UPDATE_ERROR:", error);

        return json({
          error: "Client status update failed",
        }, 500);
      }
    }

    return json({ error: "Route not found" }, 404);
  },
};
