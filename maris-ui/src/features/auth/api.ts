import { ApiError, get, post, useMock } from "@/shared/api/client";
import type { Session, UserGroup } from "./types";
import userGroups from "./userGroups.json";

/** the user groups of the demo data (mock mode) and the suggestions on the sign-on page */
export const USER_GROUPS = userGroups as UserGroup[];

/** sign on through the middleware (MARIS program MRS02A): `user` is the MARIS group ID */
export async function signOn(user: string, password: string): Promise<Session> {
  if (!useMock) return post<Session>("/auth/signon", { groupId: user.trim(), password });
  const g = USER_GROUPS.find(x => x.users.includes(user));
  if (!g) throw new Error("Unknown user ID.");
  return { user, role: g.role, group: g.group, update: ["TOWER", "SHOP", "ADMIN"].includes(g.role), signedAt: new Date().toISOString() };
}

export async function signOff(): Promise<void> {
  if (!useMock) await post<void>("/auth/signoff").catch(() => undefined);   // the session may already be gone
}

/** the session the middleware still holds (after a page reload), or null */
export async function currentSession(): Promise<Session | null> {
  if (useMock) return null;
  try { return await get<Session>("/auth/me"); }
  catch (e) { if (e instanceof ApiError && e.status === 401) return null; throw e; }
}
