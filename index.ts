
interface Env {
  DB: D1Database;
  XXXCORE_ADMIN_SECRET: string;
  ASSETS: Fetcher;
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
  // Seules les sessions administrateur valides sont acceptées.
  // Le secret permanent sert uniquement à ouvrir une session.
  return await verifySession(request, env);
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

    // ROOT - ADMIN PANEL
    if (
      request.method === "GET" &&
      url.pathname === "/"
    ) {
      return env.ASSETS.fetch(request);
    }

    // HEALTH
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

    // ADMIN CLIENT LIST AND CREATION
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

          // Création du client et journalisation dans une transaction D1.
          await env.DB.batch([
            env.DB.prepare(
              `INSERT INTO clients
               (id, username, password_hash, active, created_at)
               VALUES (?, ?, ?, 1, ?)`
            ).bind(id, username, passwordHash, createdAt),

            env.DB.prepare(
              `INSERT INTO admin_logs
               (id, action, details, created_at)
               VALUES (?, ?, ?, ?)`
            ).bind(
              crypto.randomUUID(),
              "CLIENT_CREATED",
              `Client created: ${username}`,
              createdAt
            ),
          ]);

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
        const results = await env.DB.batch([
          env.DB.prepare(
            `UPDATE client_sessions
             SET revoked = 1,
                 disconnected_at = ?
             WHERE id = ?
               AND revoked = 0`
          ).bind(now, sessionId),

          env.DB.prepare(
            `INSERT INTO admin_logs
             (id, action, details, created_at)
             SELECT ?, ?, ?, ?
             WHERE changes() > 0`
          ).bind(
            crypto.randomUUID(),
            "CLIENT_SESSION_DISCONNECTED",
            `Client session disconnected: ${sessionId}`,
            now
          ),
        ]);

        if (results[0].meta.changes === 0) {
          return json({
            error: "Session not found or already disconnected",
          }, 404);
        }

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

    // ADMIN PERMANENT CLIENT DELETE
    const clientDeleteMatch = url.pathname.match(
      /^\/admin\/clients\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i
    );

    if (
      request.method === "DELETE" &&
      clientDeleteMatch
    ) {
      const authError = await requireAdmin(request, env);
      if (authError) return authError;

      const clientId = clientDeleteMatch[1];
      const now = Math.floor(Date.now() / 1000);

      try {
        const client = await env.DB.prepare(
          `SELECT username
           FROM clients
           WHERE id = ?
           LIMIT 1`
        )
          .bind(clientId)
          .first<{ username: string }>();

        if (!client) {
          return json({ error: "Client not found" }, 404);
        }

        const results = await env.DB.batch([
          env.DB.prepare(
            `UPDATE client_sessions
             SET revoked = 1,
                 disconnected_at = ?
             WHERE client_id = ?
               AND revoked = 0`
          ).bind(now, clientId),

          env.DB.prepare(
            `DELETE FROM client_configs
             WHERE client_id = ?`
          ).bind(clientId),

          env.DB.prepare(
            `DELETE FROM client_protocol_configs
             WHERE client_id = ?`
          ).bind(clientId),

          env.DB.prepare(
            `DELETE FROM client_subscriptions
             WHERE client_id = ?`
          ).bind(clientId),

          env.DB.prepare(
            `DELETE FROM client_usage_totals
             WHERE client_id = ?`
          ).bind(clientId),

          env.DB.prepare(
            `DELETE FROM client_sessions
             WHERE client_id = ?`
          ).bind(clientId),

          env.DB.prepare(
            `DELETE FROM clients
             WHERE id = ?`
          ).bind(clientId),

          env.DB.prepare(
            `INSERT INTO admin_logs
             (id, action, details, created_at)
             VALUES (?, ?, ?, ?)`
          ).bind(
            crypto.randomUUID(),
            "CLIENT_DELETED",
            `Client permanently deleted: ${client.username} (${clientId})`,
            now
          ),
        ]);

        if (results[6].meta.changes === 0) {
          return json({
            error: "Client deletion failed",
          }, 409);
        }

        return json({
          status: "deleted",
          client_id: clientId,
          username: client.username,
          deleted_at: now,
          sessions_revoked: true,
          configurations_deleted: true,
          protocol_configs_deleted: true,
          subscriptions_deleted: true,
          usage_totals_deleted: true,
        });
      } catch (error) {
        console.error("CLIENT_DELETE_ERROR:", error);

        return json({
          error: "Unable to permanently delete client",
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
      const now = Math.floor(Date.now() / 1000);

      try {
        // Modification du statut, révocation des sessions
        // et journalisation dans une transaction D1.
        const statements = [
          env.DB.prepare(
            `UPDATE clients
             SET active = ?
             WHERE id = ?`
          ).bind(active ? 1 : 0, clientId),
        ];

        if (!active) {
          statements.push(
            env.DB.prepare(
              `UPDATE client_sessions
               SET revoked = 1,
                   disconnected_at = ?
               WHERE client_id = ?
                 AND revoked = 0`
            ).bind(now, clientId)
          );
        }

        statements.push(
          env.DB.prepare(
            `INSERT INTO admin_logs
             (id, action, details, created_at)
             SELECT ?, ?, ?, ?
             WHERE EXISTS (
               SELECT 1 FROM clients WHERE id = ?
             )`
          ).bind(
            crypto.randomUUID(),
            active ? "CLIENT_ENABLED" : "CLIENT_DISABLED",
            `Client ${clientId} ${active ? "enabled" : "disabled"}`,
            now,
            clientId
          )
        );

        const results = await env.DB.batch(statements);

        if (results[0].meta.changes === 0) {
          return json({ error: "Client not found" }, 404);
        }

        const revokedSessionCount = !active
          ? results[1].meta.changes
          : 0;

        return json({
          status: "updated",
          client: {
            id: clientId,
            active,
          },
          revoked_session_count: revokedSessionCount,
        });
      } catch (error) {
        console.error("CLIENT_STATUS_UPDATE_ERROR:", error);

        return json({
          error: "Client status update failed",
        }, 500);
      }
    }

    // ADMIN CLIENT SUBSCRIPTION MANAGEMENT
    const subscriptionMatch = url.pathname.match(
      /^\/admin\/clients\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\/subscription$/i
    );

    if (
      subscriptionMatch &&
      ["GET", "POST", "PATCH"].includes(request.method)
    ) {
      const authError = await requireAdmin(request, env);
      if (authError) return authError;

      const clientId = subscriptionMatch[1];
      const now = Math.floor(Date.now() / 1000);

      try {
        const client = await env.DB.prepare(
          `SELECT id, username, active
           FROM clients
           WHERE id = ?
           LIMIT 1`
        )
          .bind(clientId)
          .first<{
            id: string;
            username: string;
            active: number;
          }>();

        if (!client) {
          return json({ error: "Client not found" }, 404);
        }

        // CONSULT SUBSCRIPTION
        if (request.method === "GET") {
          const subscription = await env.DB.prepare(
            `SELECT
               id,
               client_id,
               plan_name,
               status,
               starts_at,
               expires_at,
               quota_bytes,
               created_at,
               updated_at
             FROM client_subscriptions
             WHERE client_id = ?
             ORDER BY created_at DESC
             LIMIT 1`
          )
            .bind(clientId)
            .first();

          const lifetimeUsage = await env.DB.prepare(
            `SELECT upload_bytes, download_bytes, updated_at
             FROM client_usage_totals
             WHERE client_id = ?
             LIMIT 1`
          )
            .bind(clientId)
            .first();

          let subscriptionUsage = null;

          if (subscription) {
            subscriptionUsage = await env.DB.prepare(
              `SELECT upload_bytes, download_bytes, updated_at
               FROM client_subscription_usage
               WHERE subscription_id = ?
               LIMIT 1`
            )
              .bind((subscription as { id: string }).id)
              .first();
          }

          return json({
            status: "ok",
            client: {
              id: client.id,
              username: client.username,
              active: client.active === 1,
            },
            subscription,
            subscription_usage: subscriptionUsage,
            lifetime_usage: lifetimeUsage,
          });
        }

        // CREATE OR RENEW SUBSCRIPTION
        if (request.method === "POST") {
          let body: {
            plan_name?: string;
            duration_days?: number;
            quota_bytes?: number;
          };

          try {
            body = await request.json();
          } catch {
            return json({ error: "Invalid JSON" }, 400);
          }

          const planName = body.plan_name?.trim();
          const durationDays = body.duration_days;
          const quotaBytes = body.quota_bytes;

          if (
            !planName ||
            planName.length > 60
          ) {
            return json({
              error: "Plan name must contain 1 to 60 characters",
            }, 400);
          }

          if (
            !Number.isSafeInteger(durationDays) ||
            durationDays! < 1 ||
            durationDays! > 3650
          ) {
            return json({
              error: "Duration must be between 1 and 3650 days",
            }, 400);
          }

          if (
            !Number.isSafeInteger(quotaBytes) ||
            quotaBytes! < 0
          ) {
            return json({
              error: "Quota must be a non-negative safe integer",
            }, 400);
          }

          const subscriptionId = crypto.randomUUID();
          const startsAt = now;
          const expiresAt = now + durationDays! * 86400;

          await env.DB.batch([
            env.DB.prepare(
              `UPDATE client_subscriptions
               SET status = 'expired',
                   updated_at = ?
               WHERE client_id = ?
                 AND status = 'active'`
            ).bind(now, clientId),

            env.DB.prepare(
              `INSERT INTO client_subscriptions (
                 id,
                 client_id,
                 plan_name,
                 status,
                 starts_at,
                 expires_at,
                 quota_bytes,
                 created_at,
                 updated_at
               )
               VALUES (?, ?, ?, 'active', ?, ?, ?, ?, ?)`
            ).bind(
              subscriptionId,
              clientId,
              planName,
              startsAt,
              expiresAt,
              quotaBytes,
              now,
              now
            ),

            env.DB.prepare(
              `INSERT INTO client_subscription_usage (
                 subscription_id,
                 client_id,
                 upload_bytes,
                 download_bytes,
                 updated_at
               )
               VALUES (?, ?, 0, 0, ?)`
            ).bind(subscriptionId, clientId, now),

            env.DB.prepare(
              `INSERT INTO admin_logs (
                 id,
                 action,
                 details,
                 created_at
               )
               VALUES (?, ?, ?, ?)`
            ).bind(
              crypto.randomUUID(),
              "SUBSCRIPTION_CREATED",
              `Subscription ${subscriptionId} created for ${client.username}; plan=${planName}; duration=${durationDays} days; quota=${quotaBytes} bytes`,
              now
            ),
          ]);

          return json({
            status: "created",
            subscription: {
              id: subscriptionId,
              client_id: clientId,
              plan_name: planName,
              status: "active",
              starts_at: startsAt,
              expires_at: expiresAt,
              quota_bytes: quotaBytes,
            },
            message: "Subscription created and usage period initialized",
          }, 201);
        }

        // SUSPEND OR CANCEL ACTIVE SUBSCRIPTION
        if (request.method === "PATCH") {
          let body: {
            status?: string;
          };

          try {
            body = await request.json();
          } catch {
            return json({ error: "Invalid JSON" }, 400);
          }

          if (
            body.status !== "suspended" &&
            body.status !== "cancelled"
          ) {
            return json({
              error: "Status must be suspended or cancelled",
            }, 400);
          }

          const results = await env.DB.batch([
            env.DB.prepare(
              `UPDATE client_subscriptions
               SET status = ?,
                   updated_at = ?
               WHERE client_id = ?
                 AND status = 'active'`
            ).bind(body.status, now, clientId),

            env.DB.prepare(
              `UPDATE client_auth_sessions
               SET revoked = 1
               WHERE client_id = ?
                 AND revoked = 0`
            ).bind(clientId),

            env.DB.prepare(
              `UPDATE client_sessions
               SET revoked = 1,
                   disconnected_at = ?
               WHERE client_id = ?
                 AND revoked = 0`
            ).bind(now, clientId),

            env.DB.prepare(
              `INSERT INTO admin_logs (
                 id,
                 action,
                 details,
                 created_at
               )
               SELECT ?, ?, ?, ?
               WHERE EXISTS (
                 SELECT 1
                 FROM client_subscriptions
                 WHERE client_id = ?
                   AND status = ?
               )`
            ).bind(
              crypto.randomUUID(),
              body.status === "suspended"
                ? "SUBSCRIPTION_SUSPENDED"
                : "SUBSCRIPTION_CANCELLED",
              `Subscription ${body.status} for ${client.username}`,
              now,
              clientId,
              body.status
            ),
          ]);

          if (results[0].meta.changes === 0) {
            return json({
              error: "No active subscription found for this client",
            }, 404);
          }

          return json({
            status: body.status,
            client_id: clientId,
            revoked_auth_sessions: results[1].meta.changes,
            revoked_vpn_sessions: results[2].meta.changes,
          });
        }
      } catch (error) {
        console.error("SUBSCRIPTION_MANAGEMENT_ERROR:", error);

        return json({
          error: "Subscription operation failed",
        }, 500);
      }
    }

    // STATIC ADMIN PANEL ASSETS
    if (
      request.method === "GET" ||
      request.method === "HEAD"
    ) {
      if (
        url.pathname !== "/health" &&
        !url.pathname.startsWith("/admin/")
      ) {
        return env.ASSETS.fetch(request);
      }
    }

    return json({ error: "Route not found" }, 404);
  },
};
