import type { Metadata } from "next";
import { cacheLife } from "next/cache";
import Link from "next/link";
import { Suspense } from "react";
import { PageHeader } from "@/components/PageHeader";
import { XpChart } from "@/components/XpChart";
import { combatFromSkills, compactXp, formatNumber } from "@/lib/format";
import { fetchPlayer, fetchPlayerGains, type PlayerGainsView, type PlayerHiscoreView } from "@/lib/game-api";

export async function generateMetadata({ params }: PageProps<"/players/[name]">): Promise<Metadata> {
  const { name } = await params;
  return { title: decodeURIComponent(name) };
}

async function cachedProfile(name: string): Promise<{ player: PlayerHiscoreView; gains: PlayerGainsView | null } | null> {
  "use cache";
  cacheLife("minutes");
  const [player, gains] = await Promise.all([fetchPlayer(name), fetchPlayerGains(name)]);
  return player ? { player, gains } : null;
}

export default function PlayerPage({ params }: PageProps<"/players/[name]">) {
  return (
    <Suspense fallback={<div className="mx-auto mt-12 h-96 max-w-6xl animate-pulse px-4" />}>
      {params.then(({ name }) => (
        <Profile name={decodeURIComponent(name)} />
      ))}
    </Suspense>
  );
}

async function Profile({ name }: { name: string }) {
  const profile = await cachedProfile(name).catch(() => null);
  if (!profile) {
    return (
      <>
        <PageHeader title="Player not found">
          Nobody called <strong className="text-mist">{name}</strong> is on the hiscores.
        </PageHeader>
        <div className="mx-auto max-w-6xl px-4 py-12 text-center">
          <Link href="/hiscores" className="btn-secondary">
            Back to hiscores
          </Link>
        </div>
      </>
    );
  }

  const { player, gains } = profile;
  const isOverall = (skill: string) => skill.toLowerCase() === "overall";
  const skills = player.skills.filter((s) => !isOverall(s.skill));
  const overall = player.skills.find((s) => isOverall(s.skill));
  const gainsBySkill = new Map(gains?.skills.map((g) => [g.skill.toLowerCase(), g]));
  const combat = combatFromSkills(skills);

  return (
    <>
      <PageHeader title={player.name}>
        Combat level {combat} &middot; Total level {formatNumber(overall?.level ?? 0)} &middot;{" "}
        {formatNumber(overall?.xp ?? 0)} XP
      </PageHeader>
      <div className="mx-auto max-w-6xl space-y-8 px-4 py-12">
        <div className="flex flex-wrap gap-3">
          <Link href={`/compare?${new URLSearchParams({ a: player.name })}`} className="btn-secondary px-4 py-2">
            Compare with another player
          </Link>
          <Link href="/hiscores?view=gains" className="btn-secondary px-4 py-2">
            Top gainers
          </Link>
        </div>

        <div className="grid gap-8 lg:grid-cols-[1.3fr_1fr]">
          <section className="panel overflow-hidden">
            <h2 className="heading border-b border-night-700 px-5 py-4 text-xl font-bold">Skills</h2>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left font-display text-xs tracking-wider text-accent uppercase">
                    <th className="px-5 py-3">Skill</th>
                    <th className="px-5 py-3 text-right">Level</th>
                    <th className="px-5 py-3 text-right">XP</th>
                    <th className="px-5 py-3 text-right">Rank</th>
                    <th className="px-5 py-3 text-right" title="XP gained in the last 7 days">
                      Week
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {player.skills.map((s) => {
                    const week = gainsBySkill.get(s.skill.toLowerCase())?.week ?? 0;
                    return (
                      <tr key={s.skill} className="border-t border-night-800 odd:bg-night-950/40">
                        <td className="px-5 py-2 font-semibold text-mist">
                          {isOverall(s.skill) ? (
                            "Overall"
                          ) : (
                            <Link
                              href={`/hiscores?skill=${encodeURIComponent(s.skill.toLowerCase())}`}
                              className="hover:text-accent-hover"
                            >
                              {s.skill}
                            </Link>
                          )}
                        </td>
                        <td className={`px-5 py-2 text-right ${s.level >= 99 ? "font-bold text-star-300" : ""}`}>
                          {formatNumber(s.level)}
                        </td>
                        <td className="px-5 py-2 text-right text-mist-dim">{formatNumber(s.xp)}</td>
                        <td className="px-5 py-2 text-right text-mist-dim">{s.rank ? formatNumber(s.rank) : "-"}</td>
                        <td className={`px-5 py-2 text-right ${week > 0 ? "text-success" : "text-mist-dim"}`}>
                          {week > 0 ? `+${compactXp(week)}` : "-"}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </section>

          <div className="space-y-8">
            <section className="panel p-5">
              <h2 className="heading text-xl font-bold">XP gained</h2>
              <div className="mt-4 grid grid-cols-3 gap-3 text-center">
                {(["day", "week", "month"] as const).map((period) => (
                  <div key={period} className="rounded-md border border-night-700 bg-night-950/60 px-2 py-3">
                    <p className="text-xs tracking-wider text-mist-dim uppercase">
                      {period === "day" ? "Today" : `This ${period}`}
                    </p>
                    <p className="mt-1 font-display text-lg font-bold text-accent">
                      {compactXp(gainsBySkill.get("overall")?.[period] ?? 0)}
                    </p>
                  </div>
                ))}
              </div>
            </section>
            <section className="panel p-5">
              <h2 className="heading text-xl font-bold">Total XP, last 30 days</h2>
              <div className="mt-4">
                <XpChart series={[{ label: player.name, colour: "#74a2f2", points: gains?.history ?? [] }]} />
              </div>
            </section>
          </div>
        </div>
      </div>
    </>
  );
}
