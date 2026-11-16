import type { Metadata } from "next";
import { cacheLife } from "next/cache";
import Link from "next/link";
import { Suspense } from "react";
import { PageHeader } from "@/components/PageHeader";
import { XpChart } from "@/components/XpChart";
import { combatFromSkills, formatNumber, one } from "@/lib/format";
import { fetchPlayer, fetchPlayerGains, type PlayerGainsView, type PlayerHiscoreView } from "@/lib/game-api";

export const metadata: Metadata = { title: "Compare players" };

const COLOURS = ["#74a2f2", "#ffb347"];

async function cachedPlayer(name: string): Promise<{ player: PlayerHiscoreView; gains: PlayerGainsView | null } | null> {
  "use cache";
  cacheLife("minutes");
  const [player, gains] = await Promise.all([fetchPlayer(name), fetchPlayerGains(name)]);
  return player ? { player, gains } : null;
}

export default function ComparePage({ searchParams }: PageProps<"/compare">) {
  return (
    <>
      <PageHeader title="Compare players">Put two players side by side, skill by skill.</PageHeader>
      <div className="mx-auto max-w-5xl px-4 py-12">
        <Suspense fallback={<div className="panel h-96 animate-pulse" />}>
          <Compare searchParams={searchParams} />
        </Suspense>
      </div>
    </>
  );
}

async function Compare({ searchParams }: Pick<PageProps<"/compare">, "searchParams">) {
  const params = await searchParams;
  const a = one(params.a)?.trim().slice(0, 32) ?? "";
  const b = one(params.b)?.trim().slice(0, 32) ?? "";

  const form = (
    <form action="/compare" className="panel flex flex-wrap items-end gap-3 p-5">
      <label className="min-w-40 flex-1">
        <span className="mb-1 block text-sm font-semibold text-accent">First player</span>
        <input name="a" defaultValue={a} required maxLength={32} className="field" />
      </label>
      <label className="min-w-40 flex-1">
        <span className="mb-1 block text-sm font-semibold text-accent">Second player</span>
        <input name="b" defaultValue={b} required maxLength={32} className="field" />
      </label>
      <button type="submit" className="btn-primary">
        Compare
      </button>
    </form>
  );

  if (!a || !b) return form;

  const [left, right] = await Promise.all([cachedPlayer(a).catch(() => null), cachedPlayer(b).catch(() => null)]);
  const missing = [!left && a, !right && b].filter(Boolean);

  return (
    <div className="space-y-8">
      {form}
      {missing.length > 0 || !left || !right ? (
        <p className="panel px-5 py-8 text-center text-mist-dim">
          Couldn&apos;t find {missing.map((m) => `"${m}"`).join(" or ")} on the hiscores.
        </p>
      ) : (
        <Comparison left={left} right={right} />
      )}
    </div>
  );
}

function Comparison({
  left,
  right,
}: {
  left: { player: PlayerHiscoreView; gains: PlayerGainsView | null };
  right: { player: PlayerHiscoreView; gains: PlayerGainsView | null };
}) {
  const rightBySkill = new Map(right.player.skills.map((s) => [s.skill.toLowerCase(), s]));
  const nonOverall = (p: PlayerHiscoreView) => p.skills.filter((s) => s.skill.toLowerCase() !== "overall");
  let leftWins = 0;
  let rightWins = 0;

  const rows = left.player.skills.map((l) => {
    const r = rightBySkill.get(l.skill.toLowerCase());
    const diff = l.xp - (r?.xp ?? 0);
    if (l.skill.toLowerCase() !== "overall") {
      if (diff > 0) leftWins++;
      else if (diff < 0) rightWins++;
    }
    return { skill: l.skill, l, r, diff };
  });

  return (
    <>
      <div className="grid grid-cols-2 gap-4 text-center">
        {[left.player, right.player].map((p, i) => (
          <Link
            key={p.name}
            href={`/players/${encodeURIComponent(p.name)}`}
            className="panel p-5 transition hover:border-star-700"
          >
            <p className="heading text-2xl font-bold" style={{ color: COLOURS[i] }}>
              {p.name}
            </p>
            <p className="mt-1 text-sm text-mist-dim">Combat {combatFromSkills(nonOverall(p))}</p>
            <p className="mt-3 font-display text-3xl font-bold text-accent">{i === 0 ? leftWins : rightWins}</p>
            <p className="text-xs tracking-wider text-mist-dim uppercase">skills ahead</p>
          </Link>
        ))}
      </div>

      <section className="panel overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="font-display text-xs tracking-wider text-accent uppercase">
              <th className="px-4 py-3 text-right">{left.player.name}</th>
              <th className="px-4 py-3 text-center">Skill</th>
              <th className="px-4 py-3 text-left">{right.player.name}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(({ skill, l, r, diff }) => (
              <tr key={skill} className="border-t border-night-800 odd:bg-night-950/40">
                <td className={`px-4 py-2 text-right ${diff > 0 ? "font-bold text-success" : ""}`}>
                  {formatNumber(l.level)}
                  <span className="ml-2 text-xs text-mist-dim">{formatNumber(l.xp)}</span>
                </td>
                <td className="px-4 py-2 text-center font-semibold capitalize text-mist-dim">{skill}</td>
                <td className={`px-4 py-2 ${diff < 0 ? "font-bold text-success" : ""}`}>
                  {formatNumber(r?.level ?? 1)}
                  <span className="ml-2 text-xs text-mist-dim">{formatNumber(r?.xp ?? 0)}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="panel p-5">
        <h2 className="heading text-xl font-bold">Total XP, last 30 days</h2>
        <div className="mt-4">
          <XpChart
            series={[left, right].map((side, i) => ({
              label: side.player.name,
              colour: COLOURS[i],
              points: side.gains?.history ?? [],
            }))}
          />
        </div>
      </section>
    </>
  );
}
