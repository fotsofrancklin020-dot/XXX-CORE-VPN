
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

async function hashPassword(password: string): Promise<string> {
  const encoder = new TextEncoder();
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iterations = 310000;

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

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/") {
      return json({
        app: "XXX-CORE-VPN",
        brand: "FRANKDEV Build",
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
      } catch {
        return json({
          status: "error",
          database: "unavailable",
        }, 503);
      }
    }

    if (
      url.pathname === "/admin/clients" &&
      (request.method === "GET" || request.method === "POST")
    ) {
      if (!env.XXXCORE_ADMIN_SECRET) {
        return json({
          error: "Admin secret not configured",
        }, 503);
      }

      const authorization = request.headers.get("Authorization");

      if (authorization !== `Bearer ${env.XXXCORE_ADMIN_SECRET}`) {
        return json({
          error: "Unauthorized",
        }, 401);
      }

      if (request.method === "POST") {
        let body: {
          username?: string;
          password?: string;
        };

        try {
          body = await request.json();
        } catch {
          return json({
            error: "Invalid JSON",
          }, 400);
        }

        const username = body.username?.trim().toLowerCase();
        const password = body.password;

        if (
          !username ||
          !/^[a-z0-9_.-]{3,32}$/.test(username)
        ) {
          return json({
            error: "Invalid username",
          }, 400);
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

          return json({
            status: "created",
            client: {
              id,
              username,
              active: true,
              created_at: createdAt,
            },
          }, 201);
        } catch {
          return json({
            error: "Could not create client. Username may already exist.",
          }, 409);
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
      } catch {
        return json({
          error: "Database query failed",
        }, 500);
      }
    }

    return json({
      error: "Route not found",
    }, 404);
  },
};
