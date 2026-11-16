import type { Metadata } from "next";
import { Suspense } from "react";
import { AutoRefresh } from "@/components/AutoRefresh";
import { PageHeader } from "@/components/PageHeader";
import { formatCountdown, formatDuration, formatNumber } from "@/lib/format";
import { fetchOnline, fetchStatus } from "@/lib/game-api";

export const metadata: Metadata = { title: "Server status" };

export default function StatusPage() {
  return (
    <>
      <PageHeader title="Server status">Is the game up? This page refreshes itself every 30 seconds.</PageHeader>
      <div className="mx-auto max-w-3xl px-4 py-12">
        <Suspense fallback={<div className="panel h-72 animate-pulse" />}>
          <Status />
        </Suspense>
        <AutoRefresh seconds={30} />
      </div>
    </>
  );
}

async function Status() {
  const [status, online] = await Promise.all([fetchStatus(), fetchOnline()]);

  if (!status) {
    return (
      <div className="panel p-8 text-center">
        <Indicator tone="bad" />
        <p className="heading mt-4 text-3xl font-bold">Offline</p>
        <p className="mt-3 text-mist-dim">
          The game server isn&apos;t answering. It may be restarting; check back in a few minutes or ask on Discord.
        </p>
      </div>
    );
  }

  const restarting = status.countdownSeconds !== null;
  const tone = restarting || status.maintenance ? "warn" : "good";
  const headline = restarting ? "Restarting soon" : status.maintenance ? "Maintenance" : "Online";

  return (
    <div className="space-y-6">
      <div className="panel p-8 text-center">
        <Indicator tone={tone} />
        <p className="heading mt-4 text-3xl font-bold">{headline}</p>
        <p className="mt-3 text-mist-dim">
          {restarting
            ? `The server restarts in about ${formatCountdown(status.countdownSeconds ?? 0)}.`
            : status.maintenance
              ? "Only staff can log in while we work on the server."
              : "Everything is running normally."}
        </p>
      </div>
      <dl className="grid gap-4 sm:grid-cols-3">
        <Stat label="Players online" value={formatNumber(online?.total ?? status.players)} />
        <Stat label="Uptime" value={formatDuration(status.uptimeSeconds)} />
        <Stat label="Logins" value={status.maintenance ? "Staff only" : "Open"} />
      </dl>
      {online && online.worlds.length > 1 && (
        <div className="panel p-5">
          <h2 className="heading text-lg font-bold">Worlds</h2>
          <ul className="mt-3 grid gap-2 text-sm sm:grid-cols-2">
            {online.worlds.map((w) => (
              <li key={w.id} className="flex justify-between border-b border-night-800 py-1">
                <span className="text-mist-dim">World {w.id}</span>
                <span className="font-semibold">{formatNumber(w.count)}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

const TONES = {
  good: "bg-success shadow-[0_0_18px_#00ff00]",
  warn: "bg-amber-400 shadow-[0_0_18px_#fbbf24]",
  bad: "bg-danger shadow-[0_0_18px_#ff4a3d]",
};

function Indicator({ tone }: { tone: keyof typeof TONES }) {
  return <span className={`mx-auto block size-5 rounded-full ${TONES[tone]}`} />;
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="panel p-5 text-center">
      <dt className="text-xs tracking-wider text-mist-dim uppercase">{label}</dt>
      <dd className="mt-1 font-display text-2xl font-bold text-accent">{value}</dd>
    </div>
  );
}
