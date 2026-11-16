import "server-only";
import fs from "node:fs";
import path from "node:path";
import type { Milestone, MilestoneStatus } from "./roadmap-shared";

export type { Milestone, MilestoneStatus, RoadmapItem } from "./roadmap-shared";
export { STATUS_LABEL, milestoneProgress, roadmapProgress } from "./roadmap-shared";

const ROADMAP_DIR = path.join(process.cwd(), "content", "roadmap");
const FRONTMATTER = /^---\r?\n([\s\S]*?)\r?\n---\r?\n?/;
const CHECKBOX = /^\s*[-*]\s+\[([ xX])\]\s+(.+?)\s*$/;

const STATUSES = new Set<MilestoneStatus>(["completed", "in-progress", "planned", "ongoing"]);

function parseMeta(block: string): Record<string, string> {
  const meta: Record<string, string> = {};
  for (const line of block.split(/\r?\n/)) {
    const idx = line.indexOf(":");
    if (idx > 0) meta[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
  }
  return meta;
}

function parseItems(body: string): { items: Milestone["items"]; rest: string } {
  const items: Milestone["items"] = [];
  const other: string[] = [];
  for (const line of body.split(/\r?\n/)) {
    const match = CHECKBOX.exec(line);
    if (match) {
      items.push({ done: match[1].toLowerCase() === "x", text: match[2] });
    } else {
      other.push(line);
    }
  }
  return { items, rest: other.join("\n").trim() };
}

function parse(slug: string, source: string): Milestone {
  const match = FRONTMATTER.exec(source);
  const meta = match ? parseMeta(match[1]) : {};
  const rawBody = match ? source.slice(match[0].length) : source;
  const { items, rest } = parseItems(rawBody);
  const status = STATUSES.has(meta.status as MilestoneStatus)
    ? (meta.status as MilestoneStatus)
    : "planned";
  return {
    slug,
    title: meta.title ?? slug,
    tagline: meta.tagline ?? "",
    status,
    when: meta.when ?? "",
    order: Number.parseInt(meta.order ?? "0", 10) || 0,
    items,
    body: rest,
  };
}

export function getAllMilestones(): Milestone[] {
  if (!fs.existsSync(/*turbopackIgnore: true*/ ROADMAP_DIR)) return [];
  return fs
    .readdirSync(/*turbopackIgnore: true*/ ROADMAP_DIR)
    .filter((file) => file.endsWith(".md"))
    .map((file) =>
      parse(
        file.replace(/\.md$/, ""),
        fs.readFileSync(/*turbopackIgnore: true*/ path.join(ROADMAP_DIR, file), "utf8"),
      ),
    )
    .sort((a, b) => a.order - b.order || a.slug.localeCompare(b.slug));
}

export function getMilestone(slug: string): Milestone | null {
  return getAllMilestones().find((m) => m.slug === slug) ?? null;
}
