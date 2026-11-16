import { randomBytes } from "node:crypto";
import { cookies } from "next/headers";
import { NextResponse, type NextRequest } from "next/server";
import { ACCOUNT_COOKIE, DISCORD_STATE_COOKIE, accountCookie } from "@/lib/account-api";
import { authorizeUrl, discordConfig } from "@/lib/discord";

const STATE_MAX_AGE = 10 * 60;

export async function GET(request: NextRequest) {
  const origin = request.nextUrl.origin;
  const store = await cookies();
  if (!store.get(ACCOUNT_COOKIE)) {
    return NextResponse.redirect(new URL("/account/login", origin));
  }
  const config = discordConfig(origin);
  if (!config) {
    return NextResponse.redirect(new URL("/account?discord=disabled", origin));
  }
  const state = randomBytes(24).toString("base64url");
  store.set(DISCORD_STATE_COOKIE, state, accountCookie(STATE_MAX_AGE));
  return NextResponse.redirect(authorizeUrl(config.clientId, config.redirectUri, state));
}
