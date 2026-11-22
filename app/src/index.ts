interface Env {
  DB: D1Database;
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
        return json({ status: "ok", database: "connected" });
      } catch {
        return json({ status: "error", database: "unavailable" }, 503);
      }
    }

    return json({ error: "Route not found" }, 404);
  },
};
