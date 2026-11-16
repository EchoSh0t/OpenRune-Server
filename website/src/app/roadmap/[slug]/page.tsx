import type { Metadata } from "next";
import { cacheLife } from "next/cache";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { renderMarkdown } from "@/lib/news";
import { getAllMilestones, getMilestone } from "@/lib/roadmap";
import {
  STATUS_LABEL,
  milestoneProgress,
  type Milestone,
  type MilestoneStatus,
} from "@/lib/roadmap-shared";

export function generateStaticParams() {
  return getAllMilestones().map((m) => ({ slug: m.slug }));
}

export async function generateMetadata({ params }: PageProps<"/roadmap/[slug]">): Promise<Metadata> {
  const milestone = getMilestone((await params).slug);
  return milestone ? { title: milestone.title, description: milestone.tagline } : {};
}

export default function MilestonePage({ params }: PageProps<"/roadmap/[slug]">) {
  return (
    <article className="mx-auto max-w-3xl px-4 py-12">
      <Link href="/roadmap" className="text-sm font-semibold text-star-300 hover:text-accent-hover">
        &larr; Roadmap
      </Link>
      <Suspense fallback={<div className="panel mt-6 h-96 animate-pulse" />}>
        {params.then(({ slug }) => (
          <Detail slug={slug} />
        ))}
      </Suspense>
    </article>
  );
}

async function Detail({ slug }: { slug: string }) {
  "use cache";
  cacheLife("hours");
  const milestone = getMilestone(slug);
  if (!milestone) notFound();
  return <MilestoneDetail milestone={milestone} />;
}

const BADGE: Record<MilestoneStatus, string> = {
  completed: "border-success/40 bg-success/10 text-success",
  "in-progress": "border-star-500/50 bg-star-500/10 text-star-300",
  planned: "border-night-600 bg-night-850 text-mist-dim",
  ongoing: "border-accent/30 bg-accent/5 text-accent",
};

function MilestoneDetail({ milestone }: { milestone: Milestone }) {
  const progress = milestoneProgress(milestone);

  return (
    <>
      <div className="mt-6 flex flex-wrap items-center gap-2 text-xs font-semibold tracking-wide uppercase">
        <span className={`rounded border px-2 py-0.5 ${BADGE[milestone.status]}`}>
          {STATUS_LABEL[milestone.status]}
        </span>
        {milestone.when && <span className="text-mist-dim">{milestone.when}</span>}
      </div>
      <h1 className="heading mt-3 text-4xl font-black">{milestone.title}</h1>
      {milestone.tagline && <p className="mt-3 text-lg text-mist-dim">{milestone.tagline}</p>}

      {progress.total > 0 && (
        <div className="panel mt-8 px-5 py-4">
          <div className="flex items-end justify-between gap-3">
            <p className="text-sm text-mist-dim">
              <span className="font-display text-xl font-bold text-accent">{progress.done}</span> of{" "}
              {progress.total} shipped
            </p>
            <p className="font-display text-2xl font-black text-star-300">{progress.percent}%</p>
          </div>
          <div className="mt-3 h-2 overflow-hidden rounded-full bg-night-800">
            <div
              className="h-full rounded-full bg-linear-to-r from-star-700 to-star-300"
              style={{ width: `${progress.percent}%` }}
            />
          </div>
        </div>
      )}

      {milestone.body && (
        <div
          className="prose-osrs mt-6"
          dangerouslySetInnerHTML={{ __html: renderMarkdown(milestone.body) }}
        />
      )}

      {milestone.items.length > 0 && (
        <ul className="panel mt-6 divide-y divide-night-800 px-2 py-1 sm:px-3">
          {milestone.items.map((item) => (
            <li key={item.text} className="flex items-start gap-3 px-3 py-3 text-sm">
              <span
                aria-hidden
                className={`mt-0.5 grid size-5 shrink-0 place-items-center rounded border text-xs font-bold ${
                  item.done
                    ? "border-success/50 bg-success/15 text-success"
                    : "border-night-600 bg-night-950 text-mist-dim"
                }`}
              >
                {item.done ? "✓" : ""}
              </span>
              <span className={item.done ? "text-mist" : "text-mist-dim"}>{item.text}</span>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
