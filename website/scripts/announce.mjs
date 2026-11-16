#!/usr/bin/env node
// Posts a news article to Discord: `npm run announce -- <slug>` (slug = file name in content/news, without .md).
// Needs DISCORD_NEWS_WEBHOOK_URL and SITE_URL in the environment or .env.local.
import fs from "node:fs";
import path from "node:path";

const root = path.dirname(path.dirname(new URL(import.meta.url).pathname));
const envFile = path.join(root, ".env.local");
if (fs.existsSync(envFile)) {
  for (const line of fs.readFileSync(envFile, "utf8").split(/\r?\n/)) {
    const match = /^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/.exec(line);
    if (match && process.env[match[1]] === undefined) process.env[match[1]] = match[2];
  }
}

const slug = process.argv[2];
const webhook = process.env.DISCORD_NEWS_WEBHOOK_URL;
const site = process.env.SITE_URL;
if (!slug) fail("Usage: npm run announce -- <news-slug>");
if (!webhook) fail("Set DISCORD_NEWS_WEBHOOK_URL in .env.local first.");
if (!site) fail("Set SITE_URL (e.g. https://example.com) in .env.local first.");

const file = path.join(root, "content", "news", `${slug}.md`);
if (!fs.existsSync(file)) fail(`No news post at ${file}`);
const source = fs.readFileSync(file, "utf8");
const meta = {};
const front = /^---\r?\n([\s\S]*?)\r?\n---/.exec(source);
for (const line of front?.[1].split(/\r?\n/) ?? []) {
  const idx = line.indexOf(":");
  if (idx > 0) meta[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
}

const url = new URL(`/news/${slug}`, site).toString();
const content = [`**${meta.title ?? slug}**`, meta.summary, url].filter(Boolean).join("\n");
const res = await fetch(webhook, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ content, allowed_mentions: { parse: [] } }),
});
if (!res.ok) fail(`Discord answered ${res.status}: ${await res.text()}`);
console.log(`Posted "${meta.title ?? slug}" to Discord.`);

function fail(message) {
  console.error(message);
  process.exit(1);
}
