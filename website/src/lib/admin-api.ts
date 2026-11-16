import "server-only";
import { cookies, headers } from "next/headers";
import { redirect } from "next/navigation";

export const SESSION_COOKIE = "elysium_staff";
export const CHALLENGE_COOKIE = "elysium_staff_challenge";
export const SESSION_MAX_AGE = 12 * 60 * 60;
export const CHALLENGE_MAX_AGE = 5 * 60;

export type Staff = { name: string; rights: "MOD" | "ADMINISTRATOR"; admin: boolean };

export type ActionResult = { ok: boolean; message: string; data?: unknown };

export type OnlinePlayer = {
  name: string;
  combatLevel: number;
  totalLevel: number;
  x: number;
  z: number;
  level: number;
  runEnergy: number;
};

export type ServerStatus = {
  uptimeSeconds: number;
  cycle: number;
  lastTickMs: number;
  avgTickMs: number;
  maxTickMs: number;
  heapUsedMb: number;
  heapMaxMb: number;
  maintenance: boolean;
  globalXpRate: number;
  supervised: boolean;
  countdownSeconds: number | null;
  scheduledAction: string | null;
  players: OnlinePlayer[];
};

export type Ban = { name: string; bannedUntil: string; permanent: boolean };

export type PlayerLookup = {
  name: string;
  characterId: number;
  accountId: number;
  accountName: string;
  rights: string;
  email: string | null;
  members: boolean;
  twoFactor: boolean;
  createdAt: string | null;
  lastLogin: string | null;
  lastLogout: string | null;
  bannedUntil: string | null;
  mutedUntil: string | null;
  x: number | null;
  z: number | null;
  level: number | null;
  online: boolean;
  skills: { name: string; level: number; xp: number }[];
  inventories: { id: string; label: string; items: { slot: number; name: string; count: number }[] }[];
  history: PlayerHistory | null;
};

export type PlayerHistory = {
  punishments: {
    kind: string;
    status: string;
    reason: string;
    issuedBy: string;
    issuedAt: string | null;
    expiresAt: string | null;
  }[];
  notes: { author: string; body: string; createdAt: string | null }[];
  addresses: { ip: string; logins: number; firstSeen: string | null; lastSeen: string | null }[];
  alts: string[];
};

export type Report = {
  id: number;
  reporter: string;
  offender: string;
  category: string;
  details: string;
  source: string;
  status: string;
  createdAt: string | null;
  handledBy: string | null;
  handledAt: string | null;
  staffComment: string | null;
};

export type StaffMember = { accountName: string; rights: string; twoFactor: boolean; characters: string[] };

export type SearchResult = { name: string; accountName: string; email: string | null; matched: string };

export type AuditEntry = { time: string; user: string; client: string; event: string };

export type Challenge = {
  accountName: string;
  enrol: boolean;
  secret: string | null;
  otpauthUri: string | null;
};

export class AdminApiUnavailable extends Error {}

const TIMEOUT_MS = 8000;

export async function clientIp(): Promise<string> {
  const h = await headers();
  return (
    h.get("cf-connecting-ip") ??
    h.get("x-real-ip") ??
    h.get("x-forwarded-for")?.split(",")[0]?.trim() ??
    "unknown"
  );
}

export function cookieOptions(maxAge: number) {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "strict" as const,
    path: "/admin",
    maxAge,
  };
}

export async function adminFetch(
  path: string,
  init: { method?: "GET" | "POST"; body?: unknown; token?: string } = {},
): Promise<Response> {
  const base = process.env.ADMIN_API_URL;
  const key = process.env.ADMIN_API_KEY;
  if (!base || !key) {
    throw new AdminApiUnavailable("ADMIN_API_URL and ADMIN_API_KEY must be set.");
  }
  const headers: Record<string, string> = { "X-Api-Key": key, "X-Client-IP": await clientIp() };
  if (init.token) headers["X-Staff-Token"] = init.token;
  if (init.body !== undefined) headers["Content-Type"] = "application/json";
  try {
    return await fetch(new URL(path, base), {
      method: init.method ?? "GET",
      headers,
      body: init.body === undefined ? undefined : JSON.stringify(init.body),
      cache: "no-store",
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (cause) {
    throw new AdminApiUnavailable("The game server could not be reached.", { cause });
  }
}

async function sessionToken(): Promise<string | undefined> {
  return (await cookies()).get(SESSION_COOKIE)?.value;
}

/** Returns the signed-in staff member, or sends the visitor to the sign-in page. */
export async function requireStaff(): Promise<{ staff: Staff; token: string }> {
  const token = await sessionToken();
  if (!token) redirect("/admin/login");
  const res = await adminFetch("/api/staff/session", { token });
  if (res.status === 401) redirect("/admin/login");
  if (!res.ok) throw new AdminApiUnavailable(`Admin API returned ${res.status}.`);
  return { staff: (await res.json()) as Staff, token };
}

export async function adminGet<T>(path: string): Promise<T | null> {
  const { token } = await requireStaff();
  const res = await adminFetch(path, { token });
  if (res.status === 401) redirect("/admin/login");
  if (res.status === 404) return null;
  if (!res.ok) throw new AdminApiUnavailable(`Admin API returned ${res.status}.`);
  return (await res.json()) as T;
}

export async function adminAction(body: Record<string, unknown>, path = "/api/action"): Promise<ActionResult> {
  const { token } = await requireStaff();
  const res = await adminFetch(path, { method: "POST", body, token });
  if (res.status === 401) redirect("/admin/login");
  return (await res.json()) as ActionResult;
}
