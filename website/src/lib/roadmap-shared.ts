export type MilestoneStatus = "completed" | "in-progress" | "planned" | "ongoing";

export type RoadmapItem = { done: boolean; text: string };

export type Milestone = {
  slug: string;
  title: string;
  tagline: string;
  status: MilestoneStatus;
  when: string;
  order: number;
  items: RoadmapItem[];
  body: string;
};

export const STATUS_LABEL: Record<MilestoneStatus, string> = {
  completed: "Completed",
  "in-progress": "In progress",
  planned: "Planned",
  ongoing: "Ongoing",
};

export function milestoneProgress(milestone: Milestone): { done: number; total: number; percent: number } {
  const total = milestone.items.length;
  const done = milestone.items.filter((item) => item.done).length;
  return { done, total, percent: total === 0 ? 0 : Math.round((done / total) * 100) };
}

/** Overall bar: completed milestones count as finished; in-progress ones use their checklist share. */
export function roadmapProgress(milestones: Milestone[]): {
  completed: number;
  total: number;
  percent: number;
} {
  const countable = milestones.filter((m) => m.status !== "ongoing");
  const total = countable.length;
  if (total === 0) return { completed: 0, total: 0, percent: 0 };
  let score = 0;
  let completed = 0;
  for (const milestone of countable) {
    if (milestone.status === "completed") {
      score += 1;
      completed += 1;
    } else if (milestone.status === "in-progress") {
      const { percent } = milestoneProgress(milestone);
      score += percent / 100;
    }
  }
  return { completed, total, percent: Math.round((score / total) * 100) };
}
