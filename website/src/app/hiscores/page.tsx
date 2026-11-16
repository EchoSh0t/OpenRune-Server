import type { Metadata } from "next";
import { cacheLife } from "next/cache";
import Link from "next/link";
import { Suspense } from "react";
import { PageHeader } from "@/components/PageHeader";
import { formatNumber, one } from "@/lib/format";
import {
  fetchGains,
  fetchHiscores,
  fetchPlayer,
  type GainPeriod,
  type GainsView,
  type HiscoresView,
  type PlayerHiscoreView,
} from "@/lib/game-api";

export const metadata: Metadata = { title: "Hiscores" };

const OVERALL = "overall";
const PERIODS: { id: GainPeriod; label: string }[] = [
  { id: "day", label: "Today" },
  { id: "week", label: "7 days" },
  { id: "month", label: "30 days" },
];

async function cachedHiscores(skill: string, page: number): Promise<HiscoresView> {
  "use cache";
  cacheLife("minutes");
  const data = await fetchHiscores(skill, page);
  if (!data) throw new Error("Hiscores unavailable");
  return data;
}

async function cachedGains(period: GainPeriod, skill: string, page: number): Promise<GainsView> {
  "use cache";
  cacheLife("minutes");
  const data = await fetchGains(period, skill, page);
  if (!data) throw new Error("Tracker unavailable");
  return data;
}

async function cachedPlayer(name: string): Promise<PlayerHiscoreView> {
  "use cache";
  cacheLife("minutes");
  const data = await fetchPlayer(name);
  if (!data) throw new Error("Player not found");
  return data;
}

export default function HiscoresPage({ searchParams }: PageProps<"/hiscores">) {
  return (
    <>
      <PageHeader title="Hiscores">
        Rankings and XP gains for every skill. Updated every few minutes.
      </PageHeader>
      <div className="mx-auto max-w-6xl px-4 py-12">
        <Suspense fallback={<LoadingPanel />}>
          <Hiscores searchParams={searchParams} />
        </Suspense>
      </div>
    </>
  );
}

async function Hiscores({ searchParams }: Pick<PageProps<"/hiscores">, "searchParams">) {
  const params = await searchParams;
  const view = one(params.view) === "gains" ? "gains" : "ranks";
  const skill = (one(params.skill) ?? OVERALL).toLowerCase();
  const page = Math.max(0, Number.parseInt(one(params.page) ?? "0", 10) || 0);
  const player = one(params.player)?.trim().slice(0, 32) ?? "";
  const requested = one(params.period);
  const period = PERIODS.find((p) => p.id === requested)?.id ?? "week";

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap gap-2">
        <ViewTab href={ranksHref({ skill })} label="Rankings" active={view === "ranks"} />
        <ViewTab href={gainsHref({ period, skill })} label="XP gains" active={view === "gains"} />
      </div>

      {view === "gains" ? (
        <GainsBoard skill={skill} period={period} page={page} />
      ) : (
        <RanksBoard skill={skill} page={page} player={player} />
      )}
    </div>
  );
}

function ViewTab({ href, label, active }: { href: string; label: string; active: boolean }) {
  return (
    <Link href={href} className={active ? "btn-primary px-5 py-2.5" : "btn-secondary px-5 py-2.5"}>
      {label}
    </Link>
  );
}

async function RanksBoard({ skill, page, player }: { skill: string; page: number; player: string }) {
  const data = await cachedHiscores(skill, page).catch(() => null);
  if (!data) return <OfflinePanel kind="hiscores" />;

  return (
    <div className="grid gap-8 lg:grid-cols-[14rem_1fr]">
      <SkillNav
        skills={[{ key: OVERALL, label: "Overall" }, ...data.skills.map((s) => ({ key: s.name.toLowerCase(), label: s.name }))]}
        active={skill}
        hrefFor={(key) => ranksHref({ skill: key })}
      />
      <div className="space-y-6">
        <form action="/hiscores" className="flex gap-3">
          <input name="player" defaultValue={player} placeholder="Search for a player" className="field" maxLength={32} />
          <button type="submit" className="btn-secondary">
            Search
          </button>
        </form>
        {player ? <PlayerLookup name={player} /> : <RanksTable data={data} skill={skill} page={page} />}
      </div>
    </div>
  );
}

async function GainsBoard({ skill, period, page }: { skill: string; period: GainPeriod; page: number }) {
  const data = await cachedGains(period, skill, page).catch(() => null);
  if (!data) return <OfflinePanel kind="gains" />;

  const title = skill === OVERALL ? "Overall" : (data.skills.find((s) => s.name.toLowerCase() === skill)?.name ?? skill);

  return (
    <div className="grid gap-8 lg:grid-cols-[14rem_1fr]">
      <SkillNav
        skills={[{ key: OVERALL, label: "Overall" }, ...data.skills.map((s) => ({ key: s.name.toLowerCase(), label: s.name }))]}
        active={skill}
        hrefFor={(key) => gainsHref({ period, skill: key })}
      />
      <div className="space-y-6">
        <div className="flex flex-wrap gap-2">
          {PERIODS.map((p) => (
            <Link
              key={p.id}
              href={gainsHref({ period: p.id, skill })}
              className={p.id === period ? "btn-primary px-4 py-2" : "btn-secondary px-4 py-2"}
            >
              {p.label}
            </Link>
          ))}
        </div>
        <div className="panel overflow-hidden">
          <h2 className="heading border-b border-night-700 px-5 py-4 text-xl font-bold">
            {title} &middot; {PERIODS.find((p) => p.id === period)?.label}
          </h2>
          {data.rows.length === 0 ? (
            <p className="px-5 py-10 text-center text-mist-dim">No XP gained in this period yet.</p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left font-display text-xs tracking-wider text-accent uppercase">
                  <th className="px-5 py-3">Rank</th>
                  <th className="px-5 py-3">Player</th>
                  <th className="px-5 py-3 text-right">XP gained</th>
                </tr>
              </thead>
              <tbody>
                {data.rows.map((row) => (
                  <tr key={row.rank} className="border-t border-night-800 odd:bg-night-950/40 hover:bg-night-850">
                    <td className="px-5 py-2.5 text-mist-dim">{formatNumber(row.rank)}</td>
                    <td className="px-5 py-2.5">
                      <Link
                        href={`/players/${encodeURIComponent(row.name)}`}
                        className="font-semibold text-mist hover:text-accent-hover"
                      >
                        {row.name}
                      </Link>
                    </td>
                    <td className="px-5 py-2.5 text-right text-success">+{formatNumber(row.gained)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <div className="flex items-center justify-between border-t border-night-700 px-5 py-3 text-sm">
            {page > 0 ? (
              <Link href={gainsHref({ period, skill, page: page - 1 })} className="font-semibold text-star-300 hover:text-accent-hover">
                &larr; Previous
              </Link>
            ) : (
              <span />
            )}
            <span className="text-mist-dim">Page {page + 1}</span>
            {data.rows.length === data.pageSize ? (
              <Link href={gainsHref({ period, skill, page: page + 1 })} className="font-semibold text-star-300 hover:text-accent-hover">
                Next &rarr;
              </Link>
            ) : (
              <span />
            )}
          </div>
        </div>
        <p className="text-xs text-mist-dim">
          Gains count from each day&apos;s first hourly snapshot up to the player&apos;s last save, so a day can start
          up to an hour late.
        </p>
      </div>
    </div>
  );
}

function SkillNav({
  skills,
  active,
  hrefFor,
}: {
  skills: { key: string; label: string }[];
  active: string;
  hrefFor: (key: string) => string;
}) {
  return (
    <nav className="panel h-fit p-3" aria-label="Skills">
      {skills.map((s) => (
        <Link
          key={s.key}
          href={hrefFor(s.key)}
          className={`block rounded px-3 py-1.5 text-sm font-semibold transition ${
            active === s.key ? "bg-night-800 text-accent-hover" : "text-mist-dim hover:bg-night-850 hover:text-star-300"
          }`}
        >
          {s.label}
        </Link>
      ))}
    </nav>
  );
}

function RanksTable({ data, skill, page }: { data: HiscoresView; skill: string; page: number }) {
  const title = skill === OVERALL ? "Overall" : (data.skills.find((s) => s.name.toLowerCase() === skill)?.name ?? skill);

  return (
    <div className="panel overflow-hidden">
      <h2 className="heading border-b border-night-700 px-5 py-4 text-xl font-bold">{title}</h2>
      {data.rows.length === 0 ? (
        <p className="px-5 py-10 text-center text-mist-dim">No ranked players here yet.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left font-display text-xs tracking-wider text-accent uppercase">
                <th className="px-5 py-3">Rank</th>
                <th className="px-5 py-3">Player</th>
                <th className="px-5 py-3 text-right">Level</th>
                <th className="px-5 py-3 text-right">XP</th>
              </tr>
            </thead>
            <tbody>
              {data.rows.map((row) => (
                <tr key={row.rank} className="border-t border-night-800 odd:bg-night-950/40 hover:bg-night-850">
                  <td className="px-5 py-2.5 text-mist-dim">{row.rank.toLocaleString()}</td>
                  <td className="px-5 py-2.5">
                    <Link
                      href={`/players/${encodeURIComponent(row.name)}`}
                      className="font-semibold text-mist hover:text-accent-hover"
                    >
                      {row.name}
                    </Link>
                  </td>
                  <td className="px-5 py-2.5 text-right">{row.level.toLocaleString()}</td>
                  <td className="px-5 py-2.5 text-right text-mist-dim">{row.xp.toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div className="flex items-center justify-between border-t border-night-700 px-5 py-3 text-sm">
        {page > 0 ? (
          <Link href={ranksHref({ skill, page: page - 1 })} className="font-semibold text-star-300 hover:text-accent-hover">
            &larr; Previous
          </Link>
        ) : (
          <span />
        )}
        <span className="text-mist-dim">Page {page + 1}</span>
        {data.rows.length === data.pageSize ? (
          <Link href={ranksHref({ skill, page: page + 1 })} className="font-semibold text-star-300 hover:text-accent-hover">
            Next &rarr;
          </Link>
        ) : (
          <span />
        )}
      </div>
    </div>
  );
}

async function PlayerLookup({ name }: { name: string }) {
  const player = await cachedPlayer(name).catch(() => null);
  if (!player) {
    return (
      <div className="panel px-5 py-10 text-center text-mist-dim">
        No player called <strong className="text-mist">{name}</strong> was found.
      </div>
    );
  }
  return (
    <div className="panel overflow-hidden">
      <div className="flex items-center justify-between border-b border-night-700 px-5 py-4">
        <h2 className="heading text-xl font-bold">{player.name}</h2>
        <div className="flex gap-4 text-sm font-semibold">
          <Link href={`/players/${encodeURIComponent(player.name)}`} className="text-star-300 hover:text-accent-hover">
            Full profile
          </Link>
          <Link href="/hiscores" className="text-star-300 hover:text-accent-hover">
            Back to rankings
          </Link>
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left font-display text-xs tracking-wider text-accent uppercase">
              <th className="px-5 py-3">Skill</th>
              <th className="px-5 py-3 text-right">Rank</th>
              <th className="px-5 py-3 text-right">Level</th>
              <th className="px-5 py-3 text-right">XP</th>
            </tr>
          </thead>
          <tbody>
            {player.skills.map((s) => (
              <tr key={s.skill} className="border-t border-night-800 odd:bg-night-950/40">
                <td className="px-5 py-2.5 font-semibold capitalize text-mist">{s.skill}</td>
                <td className="px-5 py-2.5 text-right text-mist-dim">{s.rank?.toLocaleString() ?? "-"}</td>
                <td className="px-5 py-2.5 text-right">{s.level.toLocaleString()}</td>
                <td className="px-5 py-2.5 text-right text-mist-dim">{s.xp.toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function ranksHref({ skill = OVERALL, page = 0 }: { skill?: string; page?: number }) {
  const q = new URLSearchParams();
  if (skill !== OVERALL) q.set("skill", skill);
  if (page > 0) q.set("page", String(page));
  const qs = q.toString();
  return qs ? `/hiscores?${qs}` : "/hiscores";
}

function gainsHref({ period = "week", skill = OVERALL, page = 0 }: { period?: GainPeriod; skill?: string; page?: number }) {
  const q = new URLSearchParams({ view: "gains" });
  if (period !== "week") q.set("period", period);
  if (skill !== OVERALL) q.set("skill", skill);
  if (page > 0) q.set("page", String(page));
  return `/hiscores?${q}`;
}

function LoadingPanel() {
  return <div className="panel h-96 animate-pulse" />;
}

function OfflinePanel({ kind }: { kind: "hiscores" | "gains" }) {
  return (
    <div className="panel px-5 py-16 text-center">
      <p className="heading text-2xl font-bold">{kind === "gains" ? "XP gains unavailable" : "Hiscores unavailable"}</p>
      <p className="mt-3 text-mist-dim">The game server can&apos;t be reached right now. Please try again shortly.</p>
    </div>
  );
}
