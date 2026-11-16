const LEVEL_NAMES = ["attack", "strength", "defence", "hitpoints", "ranged", "prayer", "magic"] as const;

type CombatSkills = Record<(typeof LEVEL_NAMES)[number], number>;

/** The OSRS combat level formula. */
export function combatLevel(levels: CombatSkills): number {
  const base = 0.25 * (levels.defence + levels.hitpoints + Math.floor(levels.prayer / 2));
  const melee = 0.325 * (levels.attack + levels.strength);
  const range = 0.325 * Math.floor(levels.ranged * 1.5);
  const mage = 0.325 * Math.floor(levels.magic * 1.5);
  return Math.floor(base + Math.max(melee, range, mage));
}

export function combatFromSkills(skills: { skill: string; level: number }[]): number {
  const find = (name: string) => skills.find((s) => s.skill.toLowerCase() === name)?.level;
  const levels = Object.fromEntries(
    LEVEL_NAMES.map((name) => [name, find(name) ?? (name === "hitpoints" ? 10 : 1)]),
  ) as CombatSkills;
  return combatLevel(levels);
}

export function formatNumber(value: number): string {
  return value.toLocaleString("en-GB");
}

/** 1234567 -> "1.23M", for tight table cells. */
export function compactXp(value: number): string {
  if (value >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(2)}B`;
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(2)}M`;
  if (value >= 10_000) return `${(value / 1_000).toFixed(1)}K`;
  return formatNumber(value);
}

export function formatDuration(totalSeconds: number): string {
  const days = Math.floor(totalSeconds / 86_400);
  const hours = Math.floor((totalSeconds % 86_400) / 3_600);
  const minutes = Math.floor((totalSeconds % 3_600) / 60);
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}

export function formatCountdown(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return minutes > 0 ? `${minutes}m ${String(seconds).padStart(2, "0")}s` : `${seconds}s`;
}

export function one(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}
