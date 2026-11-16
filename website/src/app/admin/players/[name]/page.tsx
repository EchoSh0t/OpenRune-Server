import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { ActionForm } from "@/components/admin/ActionForm";
import { AdminOffline, AdminShell, Section } from "@/components/admin/AdminShell";
import { PunishmentFields } from "@/components/admin/PunishmentFields";
import {
  AdminApiUnavailable,
  type PlayerHistory,
  type PlayerLookup,
  type Staff,
  adminGet,
  requireStaff,
} from "@/lib/admin-api";

export const metadata: Metadata = { title: "Player" };

export default function PlayerPage({ params }: PageProps<"/admin/players/[name]">) {
  return (
    <Suspense fallback={<div className="mx-auto mt-10 h-96 max-w-6xl animate-pulse px-4" />}>
      {params.then(({ name }) => (
        <Player name={decodeURIComponent(name)} />
      ))}
    </Suspense>
  );
}

async function Player({ name }: { name: string }) {
  let staff: Staff;
  let player: PlayerLookup | null;
  try {
    ({ staff } = await requireStaff());
    player = await adminGet<PlayerLookup>(`/api/player?${new URLSearchParams({ name })}`);
  } catch (e) {
    if (e instanceof AdminApiUnavailable) return <AdminOffline />;
    throw e;
  }

  if (!player) {
    return (
      <AdminShell staff={staff}>
        <Section title="Not found" className="max-w-xl">
          <p className="text-sm text-mist-dim">No character is called &ldquo;{name}&rdquo;.</p>
          <Link href="/admin/players" className="btn-secondary mt-4 px-4 py-2">
            Search again
          </Link>
        </Section>
      </AdminShell>
    );
  }

  const total = player.skills.reduce((sum, s) => sum + s.level, 0);
  const banned = player.bannedUntil !== null;

  return (
    <AdminShell staff={staff}>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="heading text-3xl font-bold">{player.name}</h1>
        <Badge tone={player.online ? "good" : "muted"}>{player.online ? "Online" : "Offline"}</Badge>
        {player.rights !== "NONE" && <Badge tone="info">{player.rights === "MOD" ? "Moderator" : "Administrator"}</Badge>}
        {banned && <Badge tone="bad">Banned until {player.bannedUntil}</Badge>}
        {player.mutedUntil && <Badge tone="bad">Muted until {player.mutedUntil}</Badge>}
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_1.2fr]">
        <div className="space-y-6">
          <Section title="Account">
            <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
              <Row label="Login name" value={player.accountName} />
              <Row label="Email" value={player.email ?? "None"} />
              <Row label="Authenticator" value={player.twoFactor ? "On" : "Off"} />
              <Row label="Members" value={player.members ? "Yes" : "No"} />
              <Row label="Created" value={player.createdAt ?? "Unknown"} />
              <Row label="Last login" value={player.lastLogin ?? "Never"} />
              <Row label="Last logout" value={player.lastLogout ?? "Never"} />
              <Row
                label="Position"
                value={player.x === null ? "Unknown" : `${player.x}, ${player.z}, ${player.level}`}
              />
            </dl>
          </Section>

          <Section title="Moderation">
            <div className="space-y-6">
              {player.online && (
                <ModerationBlock title="Kick">
                  <ActionForm type="kick" submit="Kick" danger hidden={{ player: player.name }} confirm={`Kick ${player.name}?`}>
                    <input name="reason" maxLength={200} placeholder="Reason (optional)" className="field flex-1" />
                  </ActionForm>
                </ModerationBlock>
              )}
              <ModerationBlock title="Mute">
                {player.mutedUntil ? (
                  <ActionForm type="unmute" submit="Unmute" hidden={{ player: player.name }} />
                ) : (
                  <ActionForm type="mute" submit="Mute" danger hidden={{ player: player.name }} confirm={`Mute ${player.name}?`}>
                    <PunishmentFields />
                  </ActionForm>
                )}
              </ModerationBlock>
              <ModerationBlock title="Ban">
                {banned ? (
                  <ActionForm type="unban" submit="Unban" hidden={{ player: player.name }} />
                ) : (
                  <ActionForm type="ban" submit="Ban" danger hidden={{ player: player.name }} confirm={`Ban ${player.name}?`}>
                    <PunishmentFields />
                  </ActionForm>
                )}
              </ModerationBlock>
            </div>
          </Section>

          {player.history && <History player={player.name} history={player.history} admin={staff.admin} />}

          {staff.admin && player.online && (
            <Section title="Admin">
              <div className="space-y-5">
                <ActionForm type="give" submit="Give" hidden={{ player: player.name }}>
                  <input name="item" required placeholder="Item name or id" className="field flex-1" />
                  <input name="count" type="number" min="1" defaultValue={1} aria-label="Amount" className="field w-24" />
                </ActionForm>
                <ActionForm type="teleport" submit="Teleport" hidden={{ player: player.name }}>
                  <input name="x" type="number" required placeholder="x" defaultValue={3222} aria-label="x" className="field w-24" />
                  <input name="z" type="number" required placeholder="z" defaultValue={3218} aria-label="z" className="field w-24" />
                  <input name="level" type="number" min="0" max="3" defaultValue={0} aria-label="level" className="field w-16" />
                </ActionForm>
              </div>
            </Section>
          )}
        </div>

        <div className="space-y-6">
          <Section title={`Skills (total ${total})`}>
            <div className="grid grid-cols-2 gap-x-6 gap-y-1 text-sm sm:grid-cols-3">
              {player.skills.map((s) => (
                <div key={s.name} className="flex justify-between border-b border-night-800 py-1">
                  <span className="text-mist-dim">{s.name}</span>
                  <span title={`${s.xp.toLocaleString("en-GB")} xp`} className="font-semibold">
                    {s.level}
                  </span>
                </div>
              ))}
            </div>
          </Section>

          {player.inventories.length === 0 ? (
            <Section title="Items">
              <p className="text-sm text-mist-dim">No saved items.</p>
            </Section>
          ) : (
            player.inventories.map((inv) => (
              <Section key={inv.id} title={`${inv.label} (${inv.items.length})`}>
                <ul className="grid max-h-80 grid-cols-1 gap-x-6 overflow-y-auto text-sm sm:grid-cols-2">
                  {inv.items.map((item) => (
                    <li key={item.slot} className="flex justify-between border-b border-night-800 py-1">
                      <span>{item.name}</span>
                      <span className="text-mist-dim">{item.count.toLocaleString("en-GB")}</span>
                    </li>
                  ))}
                </ul>
              </Section>
            ))
          )}
          {player.online && (
            <p className="text-xs text-mist-dim">
              Items and levels are from the last save, so they can lag a little behind while the player is online.
            </p>
          )}
        </div>
      </div>
    </AdminShell>
  );
}

function ModerationBlock({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h3 className="mb-2 text-xs font-semibold tracking-wide text-mist-dim uppercase">{title}</h3>
      {children}
    </div>
  );
}

const KIND_LABELS: Record<string, string> = {
  ban: "Ban",
  temp_ban: "Temporary ban",
  mute: "Mute",
  temp_mute: "Temporary mute",
  locked: "Locked",
  kick: "Kick",
};

function History({ player, history, admin }: { player: string; history: PlayerHistory; admin: boolean }) {
  return (
    <>
      <Section title={`Punishment history (${history.punishments.length})`}>
        {history.punishments.length === 0 ? (
          <p className="text-sm text-mist-dim">A clean record.</p>
        ) : (
          <ul className="max-h-96 space-y-3 overflow-y-auto text-sm">
            {history.punishments.map((p, i) => (
              <li key={i} className="border-b border-night-800 pb-3">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-semibold">{KIND_LABELS[p.kind] ?? p.kind}</span>
                  {p.status === "active" ? <Badge tone="bad">Active</Badge> : <Badge tone="muted">Ended</Badge>}
                  <span className="text-xs text-mist-dim">
                    {p.issuedAt ?? "Unknown date"} by {p.issuedBy}
                    {p.expiresAt && ` · until ${p.expiresAt}`}
                  </span>
                </div>
                {p.reason && <p className="mt-1 text-mist">{p.reason}</p>}
              </li>
            ))}
          </ul>
        )}
      </Section>

      <Section title={`Staff notes (${history.notes.length})`}>
        {history.notes.length > 0 && (
          <ul className="mb-4 max-h-80 space-y-3 overflow-y-auto text-sm">
            {history.notes.map((note, i) => (
              <li key={i} className="border-b border-night-800 pb-3">
                <p className="text-xs text-mist-dim">
                  {note.author} · {note.createdAt ?? "Unknown date"}
                </p>
                <p className="mt-1 whitespace-pre-wrap">{note.body}</p>
              </li>
            ))}
          </ul>
        )}
        <ActionForm type="note" submit="Add note" staff hidden={{ player }}>
          <textarea name="text" required maxLength={1000} rows={2} placeholder="Only staff can see notes" className="field flex-1" />
        </ActionForm>
      </Section>

      <Section title="Linked characters">
        {history.alts.length === 0 ? (
          <p className="text-sm text-mist-dim">No other characters share an address with this one.</p>
        ) : (
          <>
            <p className="mb-2 text-xs text-mist-dim">Characters that have logged in from the same address.</p>
            <ul className="flex flex-wrap gap-2 text-sm">
              {history.alts.map((alt) => (
                <li key={alt}>
                  <Link href={`/admin/players/${encodeURIComponent(alt)}`} className="rounded border border-night-600 px-2 py-0.5 text-star-300 hover:text-accent-hover">
                    {alt}
                  </Link>
                </li>
              ))}
            </ul>
          </>
        )}
        {admin && history.addresses.length > 0 && (
          <table className="mt-4 w-full text-sm">
            <thead className="text-left text-xs text-mist-dim uppercase">
              <tr>
                <th className="pb-2">Address</th>
                <th className="pb-2">Logins</th>
                <th className="pb-2">Last seen</th>
              </tr>
            </thead>
            <tbody>
              {history.addresses.map((a) => (
                <tr key={a.ip} className="border-t border-night-700">
                  <td className="py-1.5 font-mono">
                    <Link href={`/admin/search?${new URLSearchParams({ q: a.ip })}`} className="hover:text-accent-hover">
                      {a.ip}
                    </Link>
                  </td>
                  <td className="py-1.5">{a.logins}</td>
                  <td className="py-1.5 text-mist-dim">{a.lastSeen ?? "Unknown"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Section>
    </>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <>
      <dt className="text-mist-dim">{label}</dt>
      <dd>{value}</dd>
    </>
  );
}

const TONES = {
  good: "border-success/50 text-success",
  bad: "border-danger/50 text-danger",
  info: "border-star-500/50 text-star-300",
  muted: "border-night-600 text-mist-dim",
};

function Badge({ tone, children }: { tone: keyof typeof TONES; children: React.ReactNode }) {
  return <span className={`rounded border px-2 py-0.5 text-xs font-semibold ${TONES[tone]}`}>{children}</span>;
}
