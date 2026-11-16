import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { ActionForm } from "@/components/admin/ActionForm";
import { AdminOffline, AdminShell, Section } from "@/components/admin/AdminShell";
import { PunishmentFields } from "@/components/admin/PunishmentFields";
import { AdminApiUnavailable, type Ban, type Staff, adminAction, requireStaff } from "@/lib/admin-api";

export const metadata: Metadata = { title: "Bans" };

export default function BansPage() {
  return (
    <Suspense fallback={<div className="mx-auto mt-10 h-96 max-w-6xl animate-pulse px-4" />}>
      <Bans />
    </Suspense>
  );
}

async function Bans() {
  let staff: Staff;
  let bans: Ban[];
  try {
    ({ staff } = await requireStaff());
    const result = await adminAction({ type: "bans" });
    bans = result.ok ? (result.data as Ban[]) : [];
  } catch (e) {
    if (e instanceof AdminApiUnavailable) return <AdminOffline />;
    throw e;
  }

  return (
    <AdminShell staff={staff}>
      <div className="grid gap-6 lg:grid-cols-[1fr_1.4fr]">
        <Section title="Ban a player">
          <p className="mb-4 text-sm text-mist-dim">Works whether they&apos;re online or not. Online players are kicked.</p>
          <ActionForm type="ban" submit="Ban" danger confirm="Ban this player?">
            <label className="w-full text-sm">
              <span className="block text-xs text-mist-dim">Player</span>
              <input name="player" required maxLength={12} placeholder="Display name" className="field mt-1" />
            </label>
            <PunishmentFields />
          </ActionForm>
        </Section>

        <Section title={`Active bans (${bans.length})`}>
          {bans.length === 0 ? (
            <p className="text-sm text-mist-dim">Nobody is banned.</p>
          ) : (
            <table className="w-full text-sm">
              <thead className="text-left text-xs text-mist-dim uppercase">
                <tr>
                  <th className="pb-2">Name</th>
                  <th className="pb-2">Until</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {bans.map((ban) => (
                  <tr key={ban.name} className="border-t border-night-700">
                    <td className="py-2">
                      <Link href={`/admin/players/${encodeURIComponent(ban.name)}`} className="text-star-300 hover:text-accent-hover">
                        {ban.name}
                      </Link>
                    </td>
                    <td className="py-2 text-mist-dim">{ban.bannedUntil}</td>
                    <td className="py-2 text-right">
                      <ActionForm type="unban" submit="Unban" hidden={{ player: ban.name }} className="inline-block" />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Section>
      </div>
    </AdminShell>
  );
}
