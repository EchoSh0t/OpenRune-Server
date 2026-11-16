import "server-only";
import fs from "node:fs";
import path from "node:path";
import { marked } from "marked";

export type NewsPost = {
  slug: string;
  title: string;
  date: string;
  summary: string;
  body: string;
};

const NEWS_DIR = path.join(process.cwd(), "content", "news");
const FRONTMATTER = /^---\r?\n([\s\S]*?)\r?\n---\r?\n?/;

function parse(slug: string, source: string): NewsPost {
  const match = FRONTMATTER.exec(source);
  const meta: Record<string, string> = {};
  if (match) {
    for (const line of match[1].split(/\r?\n/)) {
      const idx = line.indexOf(":");
      if (idx > 0) meta[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
    }
  }
  return {
    slug,
    title: meta.title ?? slug,
    date: meta.date ?? "",
    summary: meta.summary ?? "",
    body: match ? source.slice(match[0].length) : source,
  };
}

export function getAllPosts(): NewsPost[] {
  if (!fs.existsSync(NEWS_DIR)) return [];
  return fs
    .readdirSync(NEWS_DIR)
    .filter((file) => file.endsWith(".md"))
    .map((file) => parse(file.replace(/\.md$/, ""), fs.readFileSync(path.join(NEWS_DIR, file), "utf8")))
    .sort((a, b) => b.date.localeCompare(a.date) || b.slug.localeCompare(a.slug));
}

export function getPost(slug: string): NewsPost | null {
  return getAllPosts().find((post) => post.slug === slug) ?? null;
}

export function renderMarkdown(markdown: string): string {
  return marked.parse(markdown, { async: false });
}

export function formatDate(date: string): string {
  const parsed = new Date(`${date}T00:00:00Z`);
  if (Number.isNaN(parsed.getTime())) return date;
  return parsed.toLocaleDateString("en-GB", { day: "numeric", month: "long", year: "numeric", timeZone: "UTC" });
}
