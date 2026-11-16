import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { searchPlayers } from "@/app/admin/actions";
import { AdminOffline, AdminShell, Section } from "@/components/admin/AdminShell";
import { AdminApiUnavailable, type SearchResult, type Staff, adminGet, requireStaff } from "@/lib/admin-api";
import { one } from "@/lib/format";

export const metadata: Metadata = { title: "Search" };

const MIN_QUERY = 2;

export default function SearchPage({ searchParams }: PageProps<"/admin/search">) {
  return (
    <Suspense fallback={<div className="mx-auto mt-10 h-96 max-w-6xl animate-pulse px-4" />}>
      {searchParams.then(({ q }) => (
        <Search query={one(q)?.trim() ?? ""} />
      ))}
    </Suspense>
  );
}

async function Search({ query }: { query: string }) {
  let staff: Staff;
  let results: SearchResult[] | null = null;
  try {
    ({ staff } = await requireStaff());
    if (query.length >= MIN_QUERY) {
      results = (await adminGet<SearchResult[]>(`/api/search?${new URLSearchParams({ q: query })}`)) ?? [];
    }
  } catch (e) {
    if (e instanceof AdminApiUnavailable) return <AdminOffline />;
    throw e;
  }

  return (
    <AdminShell staff={staff}>
      <Section title="Search accounts" className="max-w-3xl">
        <p className="mb-4 text-sm text-mist-dim">
          {staff.admin
            ? "Search by character name, login name, email or IP address (the start of an address works too)."
            : "Search by character name, login name or email."}
        </p>
        <form action={searchPlayers} className="flex gap-2">
          <input
            name="q"
            defaultValue={query}
            required
            minLength={MIN_QUERY}
            maxLength={64}
            placeholder={staff.admin ? "Name, email or IP" : "Name or email"}
            className="field flex-1"
          />
          <button type="submit" className="btn-primary px-5 py-2.5">
            Search
          </button>
        </form>
      </Section>

      {results && (
        <Section title={`${results.length} result${results.length === 1 ? "" : "s"}`} className="mt-6 max-w-3xl">
          {results.length === 0 ? (
            <p className="text-sm text-mist-dim">Nothing matched &ldquo;{query}&rdquo;.</p>
          ) : (
            <table className="w-full text-sm">
              <thead className="text-left text-xs text-mist-dim uppercase">
                <tr>
                  <th className="pb-2">Character</th>
                  <th className="pb-2">Login</th>
                  <th className="pb-2">Email</th>
                  <th className="pb-2">Matched on</th>
                </tr>
              </thead>
              <tbody>
                {results.map((r) => (
                  <tr key={r.name} className="border-t border-night-700">
                    <td className="py-2">
                      <Link href={`/admin/players/${encodeURIComponent(r.name)}`} className="text-star-300 hover:text-accent-hover">
                        {r.name}
                      </Link>
                    </td>
                    <td className="py-2">{r.accountName}</td>
                    <td className="py-2 text-mist-dim">{r.email ?? "None"}</td>
                    <td className="py-2 text-mist-dim">{r.matched}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Section>
      )}
    </AdminShell>
  );
}
