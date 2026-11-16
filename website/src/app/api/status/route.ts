import { fetchStatus } from "@/lib/game-api";

export async function GET() {
  const status = await fetchStatus();
  if (!status) {
    return Response.json({ online: false }, { status: 503 });
  }
  return Response.json(status, { headers: { "Cache-Control": "public, max-age=10" } });
}
