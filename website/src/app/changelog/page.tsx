import fs from "node:fs";
import path from "node:path";
import type { Metadata } from "next";
import { cacheLife } from "next/cache";
import { PageHeader } from "@/components/PageHeader";
import { renderMarkdown } from "@/lib/news";

export const metadata: Metadata = { title: "Changelog" };

const CHANGELOG = process.env.CHANGELOG_PATH || path.join(process.cwd(), "..", "CHANGELOG.md");

function changelogBody(): string | null {
  if (!fs.existsSync(/*turbopackIgnore: true*/ CHANGELOG)) return null;
  return fs
    .readFileSync(/*turbopackIgnore: true*/ CHANGELOG, "utf8")
    .replace(/^# .*\r?\n/, "")
    .trim();
}

export default async function ChangelogPage() {
  "use cache";
  cacheLife("hours");
  const body = changelogBody();

  return (
    <>
      <PageHeader title="Changelog">Every change to the game, newest first.</PageHeader>
      <div className="mx-auto max-w-3xl px-4 py-12">
        {body ? (
          <div
            className="panel prose-osrs p-6 sm:p-8"
            dangerouslySetInnerHTML={{ __html: renderMarkdown(body) }}
          />
        ) : (
          <p className="panel px-5 py-10 text-center text-mist-dim">The changelog isn&apos;t available right now.</p>
        )}
      </div>
    </>
  );
}
