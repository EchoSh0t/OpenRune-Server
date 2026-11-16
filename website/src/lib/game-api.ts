import "server-only";

export type OnlineView = { total: number; worlds: { id: number; count: number }[] };

export type SkillInfo = { id: number; name: string };

export type HiscoreRow = { rank: number; name: string; level: number; xp: number };

export type HiscoresView = {
  skill: string;
  page: number;
  pageSize: number;
  rows: HiscoreRow[];
  skills: SkillInfo[];
};

export type PlayerHiscoreView = {
  name: string;
  skills: { skill: string; rank: number | null; level: number; xp: number }[];
};

export type RegisterResult = {
  ok: boolean;
  message: string;
  code?: string;
  accountName?: string;
};

export class GameApiUnavailable extends Error {}

const TIMEOUT_MS = 8000;

export async function gameApi(path: string, init: RequestInit = {}): Promise<Response> {
  const base = process.env.GAME_API_URL;
  const key = process.env.GAME_API_KEY;
  if (!base || !key) {
    throw new GameApiUnavailable("GAME_API_URL and GAME_API_KEY must be set.");
  }
  try {
    return await fetch(new URL(path, base), {
      ...init,
      cache: "no-store",
      signal: AbortSignal.timeout(TIMEOUT_MS),
      headers: { ...init.headers, "X-Api-Key": key },
    });
  } catch (cause) {
    throw new GameApiUnavailable("The game server could not be reached.", { cause });
  }
}

export async function fetchOnline(): Promise<OnlineView | null> {
  try {
    const res = await gameApi("/api/online");
    return res.ok ? ((await res.json()) as OnlineView) : null;
  } catch {
    return null;
  }
}

export async function fetchHiscores(skill: string, page: number): Promise<HiscoresView | null> {
  const query = new URLSearchParams({ skill, page: String(page) });
  try {
    const res = await gameApi(`/api/hiscores?${query}`);
    return res.ok ? ((await res.json()) as HiscoresView) : null;
  } catch {
    return null;
  }
}

export async function fetchPlayer(name: string): Promise<PlayerHiscoreView | null> {
  try {
    const res = await gameApi(`/api/hiscores/player?${new URLSearchParams({ name })}`);
    return res.ok ? ((await res.json()) as PlayerHiscoreView) : null;
  } catch {
    return null;
  }
}

export type ServerStatus = {
  online: boolean;
  players: number;
  maintenance: boolean;
  countdownSeconds: number | null;
  uptimeSeconds: number;
};

export type GainPeriod = "day" | "week" | "month";

export type GainsView = {
  period: GainPeriod;
  skill: string;
  page: number;
  pageSize: number;
  rows: { rank: number; name: string; gained: number }[];
  skills: SkillInfo[];
};

export type SkillGain = { skill: string; xp: number; day: number; week: number; month: number };

export type PlayerGainsView = {
  name: string;
  skills: SkillGain[];
  history: { day: string; xp: number }[];
};

export type ItemSummary = { id: number; name: string; value: number };

export type MonsterSummary = { id: number; name: string; combatLevel: number };

export type DropSource = {
  monsterId: number;
  monster: string;
  combatLevel: number;
  quantity: string;
  chance: number;
  rarity: string;
};

export type ItemView = {
  id: number;
  name: string;
  examine: string;
  value: number;
  highAlch: number;
  lowAlch: number;
  weight: number;
  tradeable: boolean;
  stackable: boolean;
  equipable: boolean;
  droppedBy: DropSource[];
};

export type MonsterDrop = {
  itemId: number | null;
  name: string;
  quantity: string;
  chance: number;
  rarity: string;
  section: string;
};

export type MonsterView = {
  id: number;
  name: string;
  combatLevel: number;
  hitpoints: number;
  attack: number;
  strength: number;
  defence: number;
  magic: number;
  ranged: number;
  size: number;
  drops: MonsterDrop[];
};

async function getJson<T>(path: string): Promise<T | null> {
  try {
    const res = await gameApi(path);
    return res.ok ? ((await res.json()) as T) : null;
  } catch {
    return null;
  }
}

export function fetchStatus(): Promise<ServerStatus | null> {
  return getJson<ServerStatus>("/api/status");
}

export function fetchGains(period: GainPeriod, skill: string, page: number): Promise<GainsView | null> {
  return getJson<GainsView>(`/api/gains?${new URLSearchParams({ period, skill, page: String(page) })}`);
}

export function fetchPlayerGains(name: string, skill = "overall", days = 30): Promise<PlayerGainsView | null> {
  return getJson<PlayerGainsView>(`/api/gains/player?${new URLSearchParams({ name, skill, days: String(days) })}`);
}

export function searchItems(q: string): Promise<ItemSummary[] | null> {
  return getJson<ItemSummary[]>(`/api/items?${new URLSearchParams({ q })}`);
}

export function searchMonsters(q: string): Promise<MonsterSummary[] | null> {
  return getJson<MonsterSummary[]>(`/api/monsters?${new URLSearchParams({ q })}`);
}

export function fetchItem(id: number): Promise<ItemView | null> {
  return getJson<ItemView>(`/api/items/${id}`);
}

export function fetchMonster(id: number): Promise<MonsterView | null> {
  return getJson<MonsterView>(`/api/monsters/${id}`);
}

export function fetchReportCategories(): Promise<string[] | null> {
  return getJson<string[]>("/api/reports/categories");
}

export async function registerAccount(
  body: { username: string; password: string; email?: string },
  clientIp: string,
): Promise<RegisterResult> {
  const res = await gameApi("/api/register", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Client-IP": clientIp },
    body: JSON.stringify(body),
  });
  return (await res.json()) as RegisterResult;
}
