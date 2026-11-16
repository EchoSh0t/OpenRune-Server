import "server-only";

const AUTHORIZE_URL = "https://discord.com/oauth2/authorize";
const TOKEN_URL = "https://discord.com/api/oauth2/token";
const ME_URL = "https://discord.com/api/users/@me";

export function discordConfig(origin: string): { clientId: string; clientSecret: string; redirectUri: string } | null {
  const clientId = process.env.DISCORD_CLIENT_ID;
  const clientSecret = process.env.DISCORD_CLIENT_SECRET;
  if (!clientId || !clientSecret) return null;
  const base = process.env.SITE_URL || origin;
  return { clientId, clientSecret, redirectUri: new URL("/account/discord/callback", base).toString() };
}

export function authorizeUrl(clientId: string, redirectUri: string, state: string): string {
  const query = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    response_type: "code",
    scope: "identify",
    state,
    prompt: "none",
  });
  return `${AUTHORIZE_URL}?${query}`;
}

/** Swaps an OAuth code for the Discord user id, or null if Discord refuses. */
export async function discordUserId(
  code: string,
  config: { clientId: string; clientSecret: string; redirectUri: string },
): Promise<string | null> {
  const tokenRes = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: config.clientId,
      client_secret: config.clientSecret,
      grant_type: "authorization_code",
      code,
      redirect_uri: config.redirectUri,
    }),
    cache: "no-store",
    signal: AbortSignal.timeout(8000),
  });
  if (!tokenRes.ok) return null;
  const { access_token: accessToken } = (await tokenRes.json()) as { access_token?: string };
  if (!accessToken) return null;

  const meRes = await fetch(ME_URL, {
    headers: { Authorization: `Bearer ${accessToken}` },
    cache: "no-store",
    signal: AbortSignal.timeout(8000),
  });
  if (!meRes.ok) return null;
  const { id } = (await meRes.json()) as { id?: string };
  return id && /^[0-9]{5,25}$/.test(id) ? id : null;
}