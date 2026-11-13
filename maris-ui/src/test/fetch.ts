/* A scripted middleware for UI tests: routes by method + path, records every request. */
import { vi } from "vitest";

export interface Sent { method: string; path: string; query: URLSearchParams; body: unknown }
type Route = (req: Sent) => { status?: number; body?: unknown } | undefined;

export function fakeMiddleware(routes: Record<string, Route>) {
  const sent: Sent[] = [];
  vi.spyOn(globalThis, "fetch").mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), "http://localhost");
    const method = (init?.method || "GET").toUpperCase();
    const req: Sent = { method, path: url.pathname, query: url.searchParams, body: init?.body ? JSON.parse(String(init.body)) : undefined };
    sent.push(req);
    const route = routes[`${method} ${url.pathname}`];
    const r = route?.(req) ?? (url.pathname.endsWith("/auth/me") ? { status: 401, body: { code: "NOT_SIGNED_ON" } } : { status: 404, body: { detail: "not scripted" } });
    return new Response(r.body === undefined ? null : JSON.stringify(r.body), { status: r.status ?? 200, headers: { "Content-Type": "application/json" } });
  });
  return { sent, calls: (path: string) => sent.filter(s => s.path === path) };
}
