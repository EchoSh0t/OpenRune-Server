import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { ActionForm } from "@/components/admin/ActionForm";
import { AdminOffline, AdminShell, Section } from "@/components/admin/AdminShell";
import { AdminApiUnavailable, type Report, type Staff, adminGet, requireStaff } from "@/lib/admin-api";

export const metadata: Metadata = { title: "Reports" };

export default function ReportsPage({ searchParams }: PageProps<"/admin/reports">) {
  return (
    <Suspense fallback={<div className="mx-auto mt-10 h-96 max-w-6xl animate-pulse px-4" />}>
      {searchParams.then(({ status }) => (
        <Reports closed={status === "closed"} />
      ))}
    </Suspense>
  );
}

async function Reports({ closed }: { closed: boolean }) {
  let staff: Staff;
  let reports: Report[];
  try {
    ({ staff } = await requireStaff());
    reports = (await adminGet<Report[]>(`/api/reports?status=${closed ? "closed" : "open"}`)) ?? [];
  } catch (e) {
    if (e instanceof AdminApiUnavailable) return <AdminOffline />;
    throw e;
  }

  return (
    <AdminShell staff={staff}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="heading text-3xl font-bold">Player reports</h1>
        <div className="flex gap-2">
          <Link href="/admin/reports" className={`${closed ? "btn-secondary" : "btn-primary"} px-4 py-2`}>
            Open
          </Link>
          <Link href="/admin/reports?status=closed" className={`${closed ? "btn-primary" : "btn-secondary"} px-4 py-2`}>
            Closed
          </Link>
        </div>
      </div>

      {reports.length === 0 ? (
        <Section title={closed ? "No closed reports" : "All clear"} className="mt-6">
          <p className="text-sm text-mist-dim">{closed ? "Nothing has been closed yet." : "There are no open reports."}</p>
        </Section>
      ) : (
        <div className="mt-6 space-y-4">
          {reports.map((report) => (
            <Section key={report.id} title={`#${report.id} · ${report.category}`}>
              <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-sm">
                <dt className="text-mist-dim">Reported</dt>
                <dd>
                  <Link
                    href={`/admin/players/${encodeURIComponent(report.offender)}`}
                    className="font-semibold text-star-300 hover:text-accent-hover"
                  >
                    {report.offender}
                  </Link>
                </dd>
                <dt className="text-mist-dim">By</dt>
                <dd>
                  {report.reporter} <span className="text-mist-dim">({report.source === "game" ? "in game" : "website"})</span>
                </dd>
                <dt className="text-mist-dim">When</dt>
                <dd>{report.createdAt ?? "Unknown"}</dd>
              </dl>
              <p className="mt-3 rounded border border-night-700 bg-night-950/40 p-3 text-sm whitespace-pre-wrap">
                {report.details}
              </p>
              {closed ? (
                <p className="mt-3 text-sm text-mist-dim">
                  Closed by {report.handledBy ?? "unknown"} {report.handledAt && `on ${report.handledAt}`}
                  {report.staffComment && <span className="mt-1 block text-mist">&ldquo;{report.staffComment}&rdquo;</span>}
                </p>
              ) : (
                <ActionForm type="closeReport" submit="Close report" staff hidden={{ id: String(report.id) }} className="mt-4">
                  <input name="text" maxLength={1000} placeholder="What did you do? (optional)" className="field flex-1" />
                </ActionForm>
              )}
            </Section>
          ))}
        </div>
      )}
    </AdminShell>
  );
}
