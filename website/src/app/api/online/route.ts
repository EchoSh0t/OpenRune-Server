import { fetchOnline } from "@/lib/game-api";

export async function GET() {
  const online = await fetchOnline();
  if (!online) {
    return Response.json({ ok: false }, { status: 503 });
  }
  return Response.json(online, { headers: { "Cache-Control": "public, max-age=15" } });
}
