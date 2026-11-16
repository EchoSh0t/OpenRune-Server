import { timingSafeEqual } from "node:crypto";
import { cookies } from "next/headers";
import { NextResponse, type NextRequest } from "next/server";
import { ACCOUNT_COOKIE, DISCORD_STATE_COOKIE, type AuthResponse, accountFetch } from "@/lib/account-api";
import { discordConfig, discordUserId } from "@/lib/discord";

export async function GET(request: NextRequest) {
  const origin = request.nextUrl.origin;
  const done = (result: string) => NextResponse.redirect(new URL(`/account?discord=${result}`, origin));

  const store = await cookies();
  const expected = store.get(DISCORD_STATE_COOKIE)?.value;
  store.delete({ name: DISCORD_STATE_COOKIE, path: "/account" });
  const token = store.get(ACCOUNT_COOKIE)?.value;
  if (!token) return NextResponse.redirect(new URL("/account/login", origin));

  const state = request.nextUrl.searchParams.get("state") ?? "";
  const code = request.nextUrl.searchParams.get("code");
  if (!expected || !code || !sameString(state, expected)) return done("failed");

  const config = discordConfig(origin);
  if (!config) return done("disabled");

  try {
    const discordId = await discordUserId(code, config);
    if (!discordId) return done("failed");
    const res = await accountFetch("/api/account/discord", { method: "POST", body: { discordId }, token });
    if (res.status === 401) return NextResponse.redirect(new URL("/account/login", origin));
    const data = (await res.json()) as AuthResponse;
    if (data.ok) return done("linked");
    return done(data.message?.includes("already linked") ? "taken" : "failed");
  } catch {
    return done("failed");
  }
}

function sameString(a: string, b: string): boolean {
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  return left.length === right.length && timingSafeEqual(left, right);
}
