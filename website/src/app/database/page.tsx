import type { Metadata } from "next";
import { cacheLife } from "next/cache";
import Link from "next/link";
import { Suspense } from "react";
import { ItemIcon } from "@/components/ItemIcon";
import { PageHeader } from "@/components/PageHeader";
import { formatNumber, one } from "@/lib/format";
import { type ItemSummary, type MonsterSummary, searchItems, searchMonsters } from "@/lib/game-api";

export const metadata: Metadata = { title: "Database" };

type Kind = "items" | "monsters";

async function cachedSearch(kind: Kind, q: string): Promise<ItemSummary[] | MonsterSummary[] | null> {
  "use cache";
  cacheLife("hours");
  return kind === "items" ? searchItems(q) : searchMonsters(q);
}

export default function DatabasePage({ searchParams }: PageProps<"/database">) {
  return (
    <>
      <PageHeader title="Database">Look up any item or monster: stats, values and exactly what drops what.</PageHeader>
      <div className="mx-auto max-w-4xl px-4 py-12">
        <Suspense fallback={<div className="panel h-64 animate-pulse" />}>
          <Search searchParams={searchParams} />
        </Suspense>
      </div>
    </>
  );
}

async function Search({ searchParams }: Pick<PageProps<"/database">, "searchParams">) {
  const params = await searchParams;
  const kind: Kind = one(params.type) === "monsters" ? "monsters" : "items";
  const q = one(params.q)?.trim().slice(0, 50) ?? "";
  const results = q.length >= 2 ? await cachedSearch(kind, q).catch(() => null) : [];

  return (
    <div className="space-y-6">
      <form action="/database" className="panel flex flex-wrap gap-3 p-5">
        <select name="type" defaultValue={kind} aria-label="Search for" className="field-select w-40">
          <option value="items">Items</option>
          <option value="monsters">Monsters</option>
        </select>
        <input
          name="q"
          defaultValue={q}
          minLength={2}
          maxLength={50}
          required
          placeholder={kind === "items" ? "Abyssal whip, shark…" : "Goblin, cow…"}
          className="field min-w-48 flex-1"
        />
        <button type="submit" className="btn-primary">
          Search
        </button>
      </form>

      {q.length < 2 ? (
        <p className="text-center text-sm text-mist-dim">Type at least two letters to search.</p>
      ) : results === null ? (
        <p className="panel px-5 py-10 text-center text-mist-dim">
          The database is unavailable right now (it loads a minute after the server starts). Try again shortly.
        </p>
      ) : results.length === 0 ? (
        <p className="panel px-5 py-10 text-center text-mist-dim">Nothing matches &ldquo;{q}&rdquo;.</p>
      ) : (
        <ul className="panel divide-y divide-night-800">
          {kind === "items"
            ? (results as ItemSummary[]).map((item) => (
                <li key={item.id}>
                  <Link
                    href={`/database/items/${item.id}`}
                    className="flex items-center gap-4 px-5 py-2.5 hover:bg-night-850"
                  >
                    <ItemIcon id={item.id} name={item.name} />
                    <span className="flex-1 font-semibold">{item.name}</span>
                    <span className="text-sm text-mist-dim">{formatNumber(item.value)} gp</span>
                  </Link>
                </li>
              ))
            : (results as MonsterSummary[]).map((monster) => (
                <li key={monster.id}>
                  <Link
                    href={`/database/monsters/${monster.id}`}
                    className="flex items-center gap-4 px-5 py-3 hover:bg-night-850"
                  >
                    <span className="flex-1 font-semibold">{monster.name}</span>
                    <span className="text-sm text-mist-dim">
                      {monster.combatLevel > 0 ? `Level ${monster.combatLevel}` : "No combat level"}
                    </span>
                  </Link>
                </li>
              ))}
        </ul>
      )}
    </div>
  );
}
