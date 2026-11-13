import { ApiError, get, post } from "@/shared/api/client";
import type { Session } from "./types";

/** sign on through the middleware (MARIS program MRS02A): `user` is the MARIS group ID */
export async function signOn(user: string, password: string): Promise<Session> {
  return post<Session>("/auth/signon", { groupId: user.trim(), password });
}

export async function signOff(): Promise<void> {
  await post<void>("/auth/signoff").catch(() => undefined);   // the session may already be gone
}

/** the session the middleware still holds (after a page reload), or null */
export async function currentSession(): Promise<Session | null> {
  try { return await get<Session>("/auth/me"); }
  catch (e) { if (e instanceof ApiError && e.status === 401) return null; throw e; }
}
