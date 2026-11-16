import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Suspense } from "react";
import { AdminOffline, AdminShell, Section } from "@/components/admin/AdminShell";
import { AdminApiUnavailable, type AuditEntry, type Staff, adminGet, requireStaff } from "@/lib/admin-api";
import { one } from "@/lib/format";

export const metadata: Metadata = { title: "Audit log" };

export default function AuditPage({ searchParams }: PageProps<"/admin/audit">) {
  return (
    <Suspense fallback={<div className="mx-auto mt-10 h-96 max-w-6xl animate-pulse px-4" />}>
      {searchParams.then(({ q }) => (
        <Audit filter={one(q)?.trim() ?? ""} />
      ))}
    </Suspense>
  );
}

async function Audit({ filter }: { filter: string }) {
  let staff: Staff;
  let entries: AuditEntry[];
  try {
    ({ staff } = await requireStaff());
    if (!staff.admin) redirect("/admin");
    const query = filter ? `?${new URLSearchParams({ q: filter })}` : "";
    entries = (await adminGet<AuditEntry[]>(`/api/audit${query}`)) ?? [];
  } catch (e) {
    if (e instanceof AdminApiUnavailable) return <AdminOffline />;
    throw e;
  }

  return (
    <AdminShell staff={staff}>
      <Section title="Audit log">
        <p className="mb-4 text-sm text-mist-dim">
          Every sign-in and action taken in the admin area, newest first. Filter by staff name, player or action.
        </p>
        <form className="mb-6 flex gap-2">
          <input name="q" defaultValue={filter} maxLength={64} placeholder="e.g. ban, or a name" className="field flex-1" />
          <button type="submit" className="btn-primary px-5 py-2.5">
            Filter
          </button>
        </form>
        {entries.length === 0 ? (
          <p className="text-sm text-mist-dim">{filter ? "Nothing matched." : "Nothing has been logged yet."}</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="text-left text-xs text-mist-dim uppercase">
                <tr>
                  <th className="pb-2 pr-4">Time</th>
                  <th className="pb-2 pr-4">Staff</th>
                  <th className="pb-2 pr-4">Address</th>
                  <th className="pb-2">What happened</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((entry, i) => (
                  <tr key={i} className="border-t border-night-700 align-top">
                    <td className="py-1.5 pr-4 whitespace-nowrap text-mist-dim">{entry.time}</td>
                    <td className="py-1.5 pr-4 font-semibold">{entry.user}</td>
                    <td className="py-1.5 pr-4 font-mono text-xs text-mist-dim">{entry.client}</td>
                    <td className="py-1.5 break-words">{entry.event}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Section>
    </AdminShell>
  );
}
