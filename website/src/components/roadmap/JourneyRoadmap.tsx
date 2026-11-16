"use client";

import { useEffect, useId, useMemo, useState } from "react";
import {
  STATUS_LABEL,
  type Milestone,
  type MilestoneStatus,
  milestoneProgress,
  roadmapProgress,
} from "@/lib/roadmap-shared";
import { SITE } from "@/lib/site";

type Filter = "all" | "completed" | "in-progress" | "planned";

const FILTERS: { id: Filter; label: string }[] = [
  { id: "all", label: "All milestones" },
  { id: "completed", label: "Completed" },
  { id: "in-progress", label: "In progress" },
  { id: "planned", label: "Planned" },
];

/** Zigzag spots along the dashed path, matching the journey layout. */
const SPOTS = [
  { left: "18%", top: "16%" },
  { left: "76%", top: "26%" },
  { left: "48%", top: "45%" },
  { left: "23%", top: "65%" },
  { left: "76%", top: "81%" },
  { left: "52%", top: "94%" },
];

const PATH =
  "M180 150 C330 80 600 80 770 240 S690 400 480 435 S160 520 240 635 S650 640 760 780 S780 880 540 900";

const STATUS_CLASS: Record<MilestoneStatus, string> = {
  completed: "released",
  "in-progress": "in-progress",
  planned: "planned",
  ongoing: "in-progress",
};

const STATUS_GLYPH: Record<MilestoneStatus, string> = {
  completed: "✓",
  "in-progress": "↻",
  planned: "◇",
  ongoing: "✦",
};

function matchesFilter(status: MilestoneStatus, filter: Filter): boolean {
  if (filter === "all") return true;
  if (filter === "in-progress") return status === "in-progress" || status === "ongoing";
  if (filter === "completed") return status === "completed";
  return status === "planned";
}

function whenBadge(milestone: Milestone): string {
  if (milestone.status === "completed") return "Completed";
  return milestone.when || "TBA";
}

function footerLabel(milestone: Milestone): string {
  if (milestone.status === "completed") return "Available now";
  if (milestone.status === "ongoing" || milestone.when.toLowerCase() === "ongoing") {
    return "Continuous work";
  }
  if (milestone.when.toLowerCase().includes("soon") || milestone.when.toUpperCase() === "TBA") {
    return "Coming soon";
  }
  return "Target quarter";
}

export function JourneyRoadmap({ milestones }: { milestones: Milestone[] }) {
  const [filter, setFilter] = useState<Filter>("all");
  const [openSlug, setOpenSlug] = useState<string | null>(null);
  const titleId = useId();
  const progress = roadmapProgress(milestones);
  const open = milestones.find((m) => m.slug === openSlug) ?? null;
  const visible = useMemo(
    () => milestones.map((m, i) => ({ milestone: m, spot: SPOTS[i] ?? SPOTS[SPOTS.length - 1], index: i })),
    [milestones],
  );

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpenSlug(null);
    };
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = "";
      window.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div className="journey-roadmap">
      <div className="journey-progress" aria-label={`${progress.completed} of ${progress.total} completed`}>
        <strong>
          {progress.completed} / {progress.total} completed
        </strong>
        <div className="journey-track">
          <i style={{ width: `${progress.percent}%` }} />
        </div>
        <span>{progress.percent}%</span>
      </div>

      <div className="journey-toolbar">
        <div className="journey-filters" role="group" aria-label="Filter milestones">
          {FILTERS.map((f) => (
            <button
              key={f.id}
              type="button"
              className={filter === f.id ? "active" : undefined}
              aria-pressed={filter === f.id}
              onClick={() => setFilter(f.id)}
            >
              {f.label}
            </button>
          ))}
        </div>
        <div className="journey-legend" aria-hidden>
          <span>
            <i className="released" /> Completed
          </span>
          <span>
            <i className="in-progress" /> In progress
          </span>
          <span>
            <i className="planned" /> Planned
          </span>
        </div>
      </div>

      <div className="journey-map" role="region" aria-label={`${SITE.name} development roadmap`}>
        <p className="journey-map-title">{SITE.name} · The journey</p>
        <span className="journey-compass" aria-hidden>
          ✦ N
        </span>
        <svg className="journey-route" viewBox="0 0 1000 1000" preserveAspectRatio="none" aria-hidden>
          <path className="base" d={PATH} />
          <path className="lit" d={PATH} />
        </svg>
        <div className="journey-nodes">
          {visible.map(({ milestone, spot }) => {
            const show = matchesFilter(milestone.status, filter);
            return (
              <button
                key={milestone.slug}
                type="button"
                className={`journey-node ${STATUS_CLASS[milestone.status]}${show ? "" : " is-hidden"}`}
                style={{ left: spot.left, top: spot.top }}
                aria-hidden={!show}
                tabIndex={show ? 0 : -1}
                aria-label={`${milestone.title}, ${STATUS_GLYPH[milestone.status]} ${STATUS_LABEL[milestone.status]}, ${milestone.when || "TBA"}. Open details`}
                onClick={() => setOpenSlug(milestone.slug)}
              >
                <div className="journey-node-top">
                  <span>
                    {STATUS_GLYPH[milestone.status]} {STATUS_LABEL[milestone.status]}
                  </span>
                  <b>{whenBadge(milestone)}</b>
                </div>
                <h2>{milestone.title}</h2>
                <p>{milestone.tagline}</p>
                <footer>
                  <span>{footerLabel(milestone)}</span>
                  <b>Explore ↗</b>
                </footer>
              </button>
            );
          })}
        </div>
      </div>

      <p className="journey-footnote">
        Target dates are working estimates and may move as development progresses.
        {SITE.discordUrl ? (
          <>
            {" "}
            <a href={SITE.discordUrl} className="text-star-300 underline underline-offset-2 hover:text-accent-hover">
              Join our Discord
            </a>{" "}
            to suggest ideas and help shape what {SITE.name} builds next.
          </>
        ) : (
          <> Tell us what you want to see next.</>
        )}
      </p>

      {open && (
        <dialog className={`journey-dialog ${STATUS_CLASS[open.status]}`} open onClose={() => setOpenSlug(null)}>
          <div className="journey-dialog-scrim" onClick={() => setOpenSlug(null)} />
          <div className="journey-dialog-inner" role="document" aria-labelledby={titleId}>
            <button type="button" className="journey-dialog-close" aria-label="Close details" onClick={() => setOpenSlug(null)}>
              ×
            </button>
            <div className="journey-dialog-meta">
              <span>
                {STATUS_GLYPH[open.status]} {STATUS_LABEL[open.status]}
              </span>
              <b>
                {footerLabel(open)}
                {open.when ? `: ${open.when}` : ""}
              </b>
            </div>
            <h2 id={titleId}>{open.title}</h2>
            {open.tagline && <p className="journey-dialog-tagline">{open.tagline}</p>}
            {open.body && <p className="journey-dialog-body">{open.body}</p>}
            {open.items.length > 0 && (
              <>
                <h3>What this milestone includes</h3>
                <ul>
                  {open.items.map((item) => (
                    <li key={item.text} className={item.done ? "done" : undefined}>
                      <i aria-hidden>{item.done ? "✓" : "·"}</i>
                      <span>{item.text}</span>
                    </li>
                  ))}
                </ul>
                <p className="journey-dialog-count">
                  {milestoneProgress(open).done} of {milestoneProgress(open).total} shipped
                </p>
              </>
            )}
          </div>
        </dialog>
      )}
    </div>
  );
}
