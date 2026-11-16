import "server-only";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { clientIp } from "./admin-api";
import { gameApi } from "./game-api";

export const ACCOUNT_COOKIE = "elysium_account";
export const ACCOUNT_CHALLENGE_COOKIE = "elysium_account_challenge";
export const DISCORD_STATE_COOKIE = "elysium_discord_state";
export const ACCOUNT_MAX_AGE = 7 * 24 * 60 * 60;
export const CHALLENGE_MAX_AGE = 5 * 60;

export type AccountView = {
  name: string;
  email: string | null;
  twoFactor: boolean;
  discordLinked: boolean;
  createdAt: string | null;
  staff?: boolean;
  characters: {
    name: string;
    members: boolean;
    totalLevel: number;
    createdAt: string | null;
    banned: boolean;
    muted: boolean;
  }[];
};

export type AuthResponse = {
  ok: boolean;
  message?: string;
  token?: string;
  challenge?: string;
  restart?: boolean;
  secret?: string;
  otpauthUri?: string;
};

/** Lax so the session survives the redirect back from Discord's sign-in page. */
export function accountCookie(maxAge: number) {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path: "/account",
    maxAge,
  };
}

export async function accountToken(): Promise<string | undefined> {
  return (await cookies()).get(ACCOUNT_COOKIE)?.value;
}

export async function accountFetch(
  path: string,
  init: { method?: "GET" | "POST"; body?: unknown; token?: string } = {},
): Promise<Response> {
  const headers: Record<string, string> = { "X-Client-IP": await clientIp() };
  if (init.token) headers["X-Account-Token"] = init.token;
  if (init.body !== undefined) headers["Content-Type"] = "application/json";
  return gameApi(path, {
    method: init.method ?? "GET",
    headers,
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });
}

/** Returns the signed-in account, or sends the visitor to the sign-in page. */
export async function requireAccount(next = "/account"): Promise<{ account: AccountView; token: string }> {
  const token = await accountToken();
  const login = `/account/login?${new URLSearchParams({ next })}`;
  if (!token) redirect(login);
  const res = await accountFetch("/api/account", { token });
  if (res.status === 401) redirect(login);
  if (!res.ok) throw new Error(`Website API returned ${res.status}.`);
  return { account: (await res.json()) as AccountView, token };
}

/** Only same-site paths under /account are allowed as post-login destinations. */
export function safeNext(next: string | null | undefined): string {
  return next && /^\/account(\/[a-z/-]*)?$/.test(next) ? next : "/account";
}
