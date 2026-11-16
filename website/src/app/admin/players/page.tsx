import type { Metadata } from "next";
import { Suspense } from "react";
import { AdminOffline, AdminShell, Section } from "@/components/admin/AdminShell";
import { AdminApiUnavailable, type Staff, requireStaff } from "@/lib/admin-api";
import { findPlayer } from "../actions";

export const metadata: Metadata = { title: "Players" };

export default function PlayersPage() {
  return (
    <Suspense fallback={<div className="mx-auto mt-10 h-64 max-w-6xl animate-pulse px-4" />}>
      <Players />
    </Suspense>
  );
}

async function Players() {
  let staff: Staff;
  try {
    ({ staff } = await requireStaff());
  } catch (e) {
    if (e instanceof AdminApiUnavailable) return <AdminOffline />;
    throw e;
  }

  return (
    <AdminShell staff={staff}>
      <Section title="Find a player" className="max-w-xl">
        <p className="mb-4 text-sm text-mist-dim">
          Look up any character by display name, online or not, to see their account, levels and items.
        </p>
        <form action={findPlayer} className="flex gap-2">
          <input name="name" required maxLength={12} placeholder="Display name" className="field" autoFocus />
          <button type="submit" className="btn-primary px-5">
            Look up
          </button>
        </form>
      </Section>
    </AdminShell>
  );
}
