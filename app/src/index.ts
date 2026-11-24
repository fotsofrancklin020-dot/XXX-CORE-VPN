
interface Env {
  DB: D1Database;
  XXXCORE_ADMIN_SECRET: string;
}

const TOKEN_LIFETIME = 7 * 24 * 60 * 60;

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
    },
  });
}

function toHex(bytes: Uint8Array): string {
  return Array.from(bytes)
    .map(b => b.toString(16).padStart(2, "0"))
    .join("");
}

function fromHex(hex: string): Uint8Array {
  if (!/^[0-9a-f]+$/i.test(hex) || hex.length % 2 !== 0) {
    throw new Error("Invalid hexadecimal value");
  }

  const result = new Uint8Array(hex.length / 2);

  for (let i = 0; i < result.length; i++) {
    result[i] = parseInt(hex.substring(i * 2, i * 2 + 2), 16);
  }

  return result;
}

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = "";

  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }

  return btoa(binary)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

function base64UrlDecode(value: string): Uint8Array {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) {
    throw new Error("Invalid token encoding");
  }

  const base64 = value
    .replace(/-/g, "+")
    .replace(/_/g, "/");

  const binary = atob(
    base64 + "=".repeat((4 - base64.length % 4) % 4)
  );

  return Uint8Array.from(binary, c => c.charCodeAt(0));
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

  return `pbkdf2$${iterations}$${toHex(salt)}$${toHex(
    new Uint8Array(bits)
  )}`;
}

async function verifyPassword(
  password: string,
  storedHash: string
): Promise<boolean> {
  try {
    const parts = storedHash.split("$");

    if (parts.length !== 4 || parts[0] !== "pbkdf2") {
      return false;
    }

    const iterations = Number(parts[1]);

    if (
      !Number.isInteger(iterations) ||
      iterations < 100000 ||
      iterations > 1000000
    ) {
      return false;
    }

    const salt = fromHex(parts[2]);
    const expectedHash = fromHex(parts[3]);

    if (salt.length !== 16 || expectedHash.length !== 32) {
      return false;
    }

    const key = await crypto.subtle.importKey(
      "raw",
      new TextEncoder().encode(password),
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

    const actualHash = new Uint8Array(bits);

    let difference = 0;

    for (let i = 0; i < expectedHash.length; i++) {
      difference |= expectedHash[i] ^ actualHash[i];
    }

    return difference === 0;
  } catch {
    return false;
  }
}

async function createToken(
  env: Env,
  clientId: string,
  username: string
): Promise<string> {
  const now = Math.floor(Date.now() / 1000);

  const payload = {
    sub: clientId,
    username,
    iat: now,
    exp: now + TOKEN_LIFETIME,
  };

  const encodedPayload = base64UrlEncode(
    new TextEncoder().encode(JSON.stringify(payload))
  );

  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(env.XXXCORE_ADMIN_SECRET),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const signature = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(encodedPayload)
  );

  return `${encodedPayload}.${base64UrlEncode(
    new Uint8Array(signature)
  )}`;
}

async function verifyToken(
  env: Env,
  token: string
): Promise<{ sub: string; username: string; exp: number } | null> {
  try {
    const parts = token.split(".");

    if (parts.length !== 2) {
      return null;
    }

    const [encodedPayload, encodedSignature] = parts;

    const key = await crypto.subtle.importKey(
      "raw",
      new TextEncoder().encode(env.XXXCORE_ADMIN_SECRET),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["verify"]
    );

    const valid = await crypto.subtle.verify(
      "HMAC",
      key,
      base64UrlDecode(encodedSignature),
      new TextEncoder().encode(encodedPayload)
    );

    if (!valid) {
      return null;
    }

    const payload = JSON.parse(
      new TextDecoder().decode(base64UrlDecode(encodedPayload))
    );

    if (
      typeof payload.sub !== "string" ||
      typeof payload.username !== "string" ||
      typeof payload.exp !== "number" ||
      payload.exp <= Math.floor(Date.now() / 1000)
    ) {
      return null;
    }

    return payload;
  } catch {
    return null;
  }
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

    // CLIENT LOGIN
    if (
      request.method === "POST" &&
      url.pathname === "/auth/login"
    ) {
      if (!env.XXXCORE_ADMIN_SECRET) {
        return json({ error: "Authentication service unavailable" }, 503);
      }

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
        typeof password !== "string" ||
        password.length === 0 ||
        password.length > 128
      ) {
        return json({ error: "Username and password are required" }, 400);
      }

      try {
        const client: any = await env.DB.prepare(
          `SELECT id, username, password_hash, active
           FROM clients
           WHERE username = ?
           LIMIT 1`
        )
          .bind(username)
          .first();

        if (
          !client ||
          Number(client.active) !== 1 ||
          !(await verifyPassword(password, client.password_hash))
        ) {
          return json({ error: "Invalid username or password" }, 401);
        }

        const token = await createToken(
          env,
          client.id,
          client.username
        );

        return json({
          status: "ok",
          token,
          token_type: "Bearer",
          expires_in: TOKEN_LIFETIME,
          user: {
            id: client.id,
            username: client.username,
          },
        });
      } catch {
        return json({ error: "Login failed" }, 500);
      }
    }

    // CHECK CURRENT SESSION
    if (
      request.method === "GET" &&
      url.pathname === "/auth/me"
    ) {
      if (!env.XXXCORE_ADMIN_SECRET) {
        return json({ error: "Authentication service unavailable" }, 503);
      }

      const authorization = request.headers.get("Authorization");
      const match = authorization?.match(/^Bearer (.+)$/);

      if (!match) {
        return json({ error: "Unauthorized" }, 401);
      }

      const payload = await verifyToken(env, match[1]);

      if (!payload) {
        return json({ error: "Invalid or expired token" }, 401);
      }

      try {
        const client: any = await env.DB.prepare(
          `SELECT id, username, active
           FROM clients
           WHERE id = ?
           LIMIT 1`
        )
          .bind(payload.sub)
          .first();

        if (!client || Number(client.active) !== 1) {
          return json({ error: "Account inactive or not found" }, 401);
        }

        return json({
          status: "ok",
          user: {
            id: client.id,
            username: client.username,
          },
        });
      } catch {
        return json({ error: "Database query failed" }, 500);
      }
    }

    // ADMIN CLIENT MANAGEMENT
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
