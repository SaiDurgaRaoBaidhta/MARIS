/* fetch client. Every feature's api.ts goes through here.
 * The middleware behind /api (dev: Vite proxy, prod: nginx), session cookie + CSRF token. All data comes from MARIS through it. */
import type { components } from "@/api/generated/schema";

export type Schemas = components["schemas"];

/** the middleware's API root */
export const API = "/api/v1";

/** an error the middleware reported (RFC 9457 problem detail): `message` is ready to show */
export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly requestId?: string;
  constructor(message: string, status: number, code?: string, requestId?: string) {
    super(message); this.status = status; this.code = code; this.requestId = requestId;
  }
}

/* the session ended (signed off elsewhere, timed out, middleware restarted): the app goes back to sign-on */
const expired = new Set<() => void>();
export function onSessionExpired(f: () => void): () => void { expired.add(f); return () => expired.delete(f); }

const cache = new Map<string, Promise<unknown>>();
/** GET a static JSON document once per page load (screen layouts) */
export function getJson<T>(url: string): Promise<T> {
  if (!cache.has(url)) cache.set(url, fetch(url).then(r => { if (!r.ok) throw new Error(`${url}: ${r.status}`); return r.json(); }));
  return cache.get(url) as Promise<T>;
}

const cookie = (name: string) => document.cookie.split("; ").find(c => c.startsWith(name + "="))?.slice(name.length + 1);

/** the CSRF token the middleware set as cookie XSRF-TOKEN; asked for once when missing */
async function csrfToken(): Promise<string> {
  let t = cookie("XSRF-TOKEN");
  if (!t) { await fetch(`${API}/auth/csrf`, { credentials: "same-origin" }); t = cookie("XSRF-TOKEN"); }
  return t ? decodeURIComponent(t) : "";
}

async function call<T>(method: "GET" | "POST", path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (method !== "GET") { headers["Content-Type"] = "application/json"; headers["X-XSRF-TOKEN"] = await csrfToken(); }
  let r: Response;
  try {
    r = await fetch(API + path, { method, headers, credentials: "same-origin", body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError("The MARIS middleware cannot be reached. Check the connection and try again.", 0, "NETWORK");
  }
  if (r.status === 204) return undefined as T;
  const text = await r.text();
  let data: unknown = undefined;
  try { data = text ? JSON.parse(text) : undefined; } catch { /* not JSON: a proxy error page */ }
  if (r.ok) return data as T;

  const p = (data ?? {}) as { detail?: string; title?: string; code?: string; requestId?: string };
  if (r.status === 401 && p.code === "NOT_SIGNED_ON") expired.forEach(f => f());
  const msg = p.detail || p.title || (r.status === 502 || r.status === 503 || r.status === 504
    ? "The MARIS system is not answering. Try again in a moment." : `The request failed (HTTP ${r.status}).`);
  throw new ApiError(p.requestId && r.status >= 500 && r.status !== 501 ? `${msg} (request ${p.requestId})` : msg, r.status, p.code, p.requestId);
}

export const get = <T>(path: string) => call<T>("GET", path);
export const post = <T>(path: string, body?: unknown) => call<T>("POST", path, body);
