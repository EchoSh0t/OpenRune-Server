import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { AccountForm, Field } from "@/components/account/AccountForm";
import { requireAccount } from "@/lib/account-api";
import { fetchReportCategories } from "@/lib/game-api";
import { submitReport } from "../actions";

export const metadata: Metadata = { title: "Report a player" };

export default function ReportPage() {
  return (
    <div className="mx-auto max-w-xl px-4 py-12">
      <Link href="/account" className="text-sm font-semibold text-star-300 hover:text-accent-hover">
        &larr; My account
      </Link>
      <Suspense fallback={<div className="panel mt-6 h-96 animate-pulse" />}>
        <Report />
      </Suspense>
    </div>
  );
}

async function Report() {
  await requireAccount("/account/report");
  const categories = (await fetchReportCategories()) ?? ["Other"];

  return (
    <div className="panel mt-6 p-8">
      <h1 className="heading text-2xl font-bold">Report a player</h1>
      <p className="mt-2 text-sm text-mist-dim">
        Reports go straight to the staff team. You can also report in game with{" "}
        <code className="text-mist">::report name, what happened</code>.
      </p>
      <AccountForm action={submitReport} submit="Send report" pendingLabel="Sending…" className="mt-6">
        <Field label="Player name">
          <input name="offender" required maxLength={12} className="field" />
        </Field>
        <Field label="What happened?">
          <select name="category" required defaultValue="" className="field-select">
            <option value="" disabled>
              Pick one
            </option>
            {categories.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Details" hint="Where and when it happened, and anything staff should look at. Up to 1000 characters.">
          <textarea name="details" required minLength={10} maxLength={1000} rows={6} className="field" />
        </Field>
      </AccountForm>
    </div>
  );
}
