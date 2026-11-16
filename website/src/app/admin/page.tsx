import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { ActionForm } from "@/components/admin/ActionForm";
import { AdminOffline, AdminShell, Section } from "@/components/admin/AdminShell";
import { AutoRefresh } from "@/components/AutoRefresh";
import { AdminApiUnavailable, type ServerStatus, type Staff, adminGet, requireStaff } from "@/lib/admin-api";
import { findPlayer } from "./actions";

export const metadata: Metadata = { title: "Dashboard" };

export default function AdminDashboardPage() {
  return (
    <Suspense fallback={<div className="mx-auto mt-10 h-96 max-w-6xl animate-pulse px-4" />}>
      <Dashboard />
    </Suspense>
  );
}

async function Dashboard() {
  let staff: Staff;
  let status: ServerStatus | null;
  let openReports: number;
  try {
    ({ staff } = await requireStaff());
    const [statusResult, reports] = await Promise.all([
      adminGet<ServerStatus>("/api/status"),
      adminGet<{ open: number }>("/api/reports/count"),
    ]);
    status = statusResult;
    openReports = reports?.open ?? 0;
  } catch (e) {
    if (e instanceof AdminApiUnavailable) return <AdminOffline />;
    throw e;
  }

  return (
    <AdminShell staff={staff}>
      <AutoRefresh seconds={5} />
      {openReports > 0 && (
        <Link
          href="/admin/reports"
          className="panel mb-6 flex items-center justify-between border-accent/50 p-4 text-sm hover:border-accent"
        >
          <span>
            <span className="font-semibold text-accent">{openReports}</span> open player report{openReports === 1 ? "" : "s"}{" "}
            waiting for staff.
          </span>
          <span className="font-semibold text-star-300">Review →</span>
        </Link>
      )}
      {status ? <StatusPanels status={status} admin={staff.admin} /> : <p>The server is still starting.</p>}
    </AdminShell>
  );
}

function StatusPanels({ status, admin }: { status: ServerStatus; admin: boolean }) {
  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Players online" value={String(status.players.length)} />
        <Stat label="Uptime" value={formatUptime(status.uptimeSeconds)} />
        <Stat
          label="Tick time"
          value={`${status.avgTickMs.toFixed(1)} ms`}
          detail={`max ${status.maxTickMs.toFixed(1)} ms`}
        />
        <Stat label="Memory" value={`${status.heapUsedMb} MB`} detail={`of ${status.heapMaxMb} MB`} />
      </div>

      {(status.maintenance || status.countdownSeconds !== null) && (
        <div className="panel border-danger/50 p-4 text-sm">
          {status.maintenance && <p className="text-danger">Maintenance mode is on: only staff can log in.</p>}
          {status.countdownSeconds !== null && (
            <p className="text-danger">
              {status.scheduledAction ?? "Update"} in {status.countdownSeconds}s.
            </p>
          )}
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <Section title="Online players">
          <form action={findPlayer} className="mb-4 flex gap-2">
            <input name="name" placeholder="Look up any player…" maxLength={12} className="field" />
            <button type="submit" className="btn-secondary px-4">
              Find
            </button>
          </form>
          {status.players.length === 0 ? (
            <p className="text-sm text-mist-dim">Nobody is online.</p>
          ) : (
            <table className="w-full text-sm">
              <thead className="text-left text-xs text-mist-dim uppercase">
                <tr>
                  <th className="pb-2">Name</th>
                  <th className="pb-2">Combat</th>
                  <th className="pb-2">Total</th>
                  <th className="pb-2">Position</th>
                </tr>
              </thead>
              <tbody>
                {status.players.map((p) => (
                  <tr key={p.name} className="border-t border-night-700">
                    <td className="py-2">
                      <Link href={`/admin/players/${encodeURIComponent(p.name)}`} className="text-star-300 hover:text-accent-hover">
                        {p.name}
                      </Link>
                    </td>
                    <td className="py-2">{p.combatLevel}</td>
                    <td className="py-2">{p.totalLevel}</td>
                    <td className="py-2 font-mono text-xs text-mist-dim">
                      {p.x}, {p.z}, {p.level}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Section>

        {admin ? (
          <div className="space-y-6">
            <Section title="Broadcast">
              <ActionForm type="broadcast" submit="Send">
                <input name="text" required maxLength={200} placeholder="Message to every player" className="field flex-1" />
              </ActionForm>
            </Section>

            <Section title="Server">
              <div className="space-y-5">
                <ActionForm
                  type="maintenance"
                  submit={status.maintenance ? "Turn off maintenance" : "Turn on maintenance"}
                  danger={!status.maintenance}
                  confirm={status.maintenance ? undefined : "Only staff will be able to log in. Continue?"}
                  hidden={{ enabled: String(!status.maintenance) }}
                />
                <ActionForm type="xpRate" submit="Set XP rate">
                  <label className="text-sm">
                    <span className="block text-xs text-mist-dim">XP rate (now {status.globalXpRate}x)</span>
                    <input
                      name="rate"
                      type="number"
                      step="0.1"
                      min="0.1"
                      max="100"
                      required
                      defaultValue={status.globalXpRate}
                      className="field mt-1 w-28"
                    />
                  </label>
                </ActionForm>
                <ActionForm
                  type="schedule"
                  submit="Start countdown"
                  danger
                  confirm="Players will see the update timer and be logged out when it ends. Continue?"
                >
                  <label className="text-sm">
                    <span className="block text-xs text-mist-dim">Action</span>
                    <select name="mode" className="field-select mt-1">
                      <option value="restart" disabled={!status.supervised}>
                        Restart
                      </option>
                      <option value="rebuild" disabled={!status.supervised}>
                        Rebuild & restart
                      </option>
                      <option value="shutdown">Shutdown</option>
                    </select>
                  </label>
                  <label className="text-sm">
                    <span className="block text-xs text-mist-dim">Seconds</span>
                    <input name="seconds" type="number" min="0" max="3600" defaultValue={120} className="field mt-1 w-24" />
                  </label>
                </ActionForm>
                {status.countdownSeconds !== null && <ActionForm type="cancelSchedule" submit="Cancel countdown" />}
              </div>
            </Section>
          </div>
        ) : (
          <Section title="Moderator tools">
            <p className="text-sm text-mist-dim">
              Open a player to kick, mute or ban them, or manage bans on the{" "}
              <Link href="/admin/bans" className="text-star-300 hover:text-accent-hover">
                Bans
              </Link>{" "}
              page. Server controls are for administrators.
            </p>
          </Section>
        )}
      </div>
    </div>
  );
}

function Stat({ label, value, detail }: { label: string; value: string; detail?: string }) {
  return (
    <div className="panel p-4">
      <p className="text-xs text-mist-dim uppercase">{label}</p>
      <p className="mt-1 font-display text-2xl font-bold text-accent">{value}</p>
      {detail && <p className="text-xs text-mist-dim">{detail}</p>}
    </div>
  );
}

function formatUptime(seconds: number): string {
  const d = Math.floor(seconds / 86400);
  const h = Math.floor((seconds % 86400) / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (d > 0) return `${d}d ${h}h`;
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}
