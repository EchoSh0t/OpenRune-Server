import type { Metadata } from "next";
import { JourneyRoadmap } from "@/components/roadmap/JourneyRoadmap";
import { SITE } from "@/lib/site";
import { getAllMilestones } from "@/lib/roadmap";

export const metadata: Metadata = {
  title: "Roadmap",
  description: `From the first foundations to what comes next on ${SITE.name}.`,
};

export default async function RoadmapPage() {
  const milestones = getAllMilestones();

  return (
    <div className="journey-page">
      <header className="journey-hero">
        <p className="journey-eyebrow">The world we&apos;re building together</p>
        <h1>Roadmap</h1>
        <p>
          From our first foundations to what comes next. Explore each milestone, discover what&apos;s in
          development, and help shape {SITE.name}&apos;s future.
        </p>
      </header>
      {milestones.length === 0 ? (
        <p className="mx-auto max-w-3xl px-4 text-mist-dim">The roadmap is being written. Check back soon.</p>
      ) : (
        <JourneyRoadmap milestones={milestones} />
      )}
    </div>
  );
}
