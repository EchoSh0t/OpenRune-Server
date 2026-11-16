import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Suspense } from "react";
import { ActionForm } from "@/components/admin/ActionForm";
import { AdminOffline, AdminShell, Section } from "@/components/admin/AdminShell";
import { AdminApiUnavailable, type Staff, type StaffMember, adminGet, requireStaff } from "@/lib/admin-api";

export const metadata: Metadata = { title: "Staff" };

const RANKS: Record<string, string> = { MOD: "Moderator", ADMINISTRATOR: "Administrator" };

export default function StaffPage() {
  return (
    <Suspense fallback={<div className="mx-auto mt-10 h-96 max-w-6xl animate-pulse px-4" />}>
      <StaffList />
    </Suspense>
  );
}

async function StaffList() {
  let staff: Staff;
  let members: StaffMember[];
  try {
    ({ staff } = await requireStaff());
    if (!staff.admin) redirect("/admin");
    members = (await adminGet<StaffMember[]>("/api/staff/members")) ?? [];
  } catch (e) {
    if (e instanceof AdminApiUnavailable) return <AdminOffline />;
    throw e;
  }

  return (
    <AdminShell staff={staff}>
      <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <Section title={`Staff team (${members.length})`}>
          <table className="w-full text-sm">
            <thead className="text-left text-xs text-mist-dim uppercase">
              <tr>
                <th className="pb-2">Account</th>
                <th className="pb-2">Rank</th>
                <th className="pb-2">Authenticator</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {members.map((m) => (
                <tr key={m.accountName} className="border-t border-night-700 align-top">
                  <td className="py-2">
                    <span className="font-semibold">{m.accountName}</span>
                    {m.characters.length > 0 && (
                      <span className="block text-xs text-mist-dim">{m.characters.join(", ")}</span>
                    )}
                  </td>
                  <td className="py-2">{RANKS[m.rights] ?? m.rights}</td>
                  <td className={`py-2 ${m.twoFactor ? "text-success" : "text-danger"}`}>{m.twoFactor ? "On" : "Off"}</td>
                  <td className="py-2 text-right">
                    {m.rights === "MOD" && (
                      <ActionForm
                        type="setRights"
                        submit="Remove"
                        danger
                        staff
                        hidden={{ player: m.accountName, rights: "NONE" }}
                        confirm={`Remove ${m.accountName} from the staff team?`}
                        className="inline-block"
                      />
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="mt-4 text-xs text-mist-dim">
            Administrators can only be changed in the database. Rank changes sign the player out of this admin area
            and apply in game the next time they log in.
          </p>
        </Section>

        <div className="space-y-6">
          <Section title="Make someone a moderator">
            <ActionForm type="setRights" submit="Promote" staff hidden={{ rights: "MOD" }} confirm="Make this player a moderator?">
              <input name="player" required maxLength={32} placeholder="Login or character name" className="field flex-1" />
            </ActionForm>
          </Section>

          <Section title="Reset an authenticator">
            <p className="mb-4 text-sm text-mist-dim">
              For players who have lost their phone. Check who they are first: this removes the authenticator from
              their whole account.
            </p>
            <ActionForm
              type="resetTwoFactor"
              submit="Reset"
              danger
              staff
              confirm="Remove the authenticator from this account?"
            >
              <input name="player" required maxLength={32} placeholder="Login or character name" className="field flex-1" />
            </ActionForm>
          </Section>
        </div>
      </div>
    </AdminShell>
  );
}
