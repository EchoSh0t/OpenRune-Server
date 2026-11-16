import type { Metadata } from "next";
import { cacheLife } from "next/cache";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { ItemIcon } from "@/components/ItemIcon";
import { Rarity } from "@/components/Rarity";
import { formatNumber } from "@/lib/format";
import { fetchItem, type ItemView } from "@/lib/game-api";

async function cachedItem(id: number): Promise<ItemView | null> {
  "use cache";
  cacheLife("hours");
  return fetchItem(id);
}

export async function generateMetadata({ params }: PageProps<"/database/items/[id]">): Promise<Metadata> {
  const item = await cachedItem(Number((await params).id)).catch(() => null);
  return { title: item?.name ?? "Item" };
}

export default function ItemPage({ params }: PageProps<"/database/items/[id]">) {
  return (
    <div className="mx-auto max-w-4xl px-4 py-12">
      <Link href="/database" className="text-sm font-semibold text-star-300 hover:text-accent-hover">
        &larr; Database
      </Link>
      <Suspense fallback={<div className="panel mt-6 h-96 animate-pulse" />}>
        {params.then(({ id }) => (
          <Item id={Number(id)} />
        ))}
      </Suspense>
    </div>
  );
}

async function Item({ id }: { id: number }) {
  if (!Number.isInteger(id) || id < 0) notFound();
  const item = await cachedItem(id).catch(() => null);
  if (!item) notFound();

  return (
    <div className="mt-6 space-y-8">
      <div className="flex items-center gap-5">
        <div className="panel flex size-20 items-center justify-center">
          <ItemIcon id={item.id} name={item.name} size={48} />
        </div>
        <div>
          <h1 className="heading text-4xl font-black">{item.name}</h1>
          {item.examine && <p className="mt-1 text-mist-dim italic">{item.examine}</p>}
        </div>
      </div>

      <dl className="grid gap-4 sm:grid-cols-4">
        <Stat label="Value" value={`${formatNumber(item.value)} gp`} />
        <Stat label="High alch" value={`${formatNumber(item.highAlch)} gp`} />
        <Stat label="Low alch" value={`${formatNumber(item.lowAlch)} gp`} />
        <Stat label="Weight" value={`${item.weight.toFixed(item.weight % 1 === 0 ? 0 : 3)} kg`} />
      </dl>

      <div className="flex flex-wrap gap-2 text-xs font-semibold">
        <Tag on={item.tradeable}>Tradeable</Tag>
        <Tag on={item.stackable}>Stackable</Tag>
        <Tag on={item.equipable}>Equipable</Tag>
        <span className="rounded border border-night-600 px-2 py-0.5 text-mist-dim">Item id {item.id}</span>
      </div>

      <section className="panel overflow-hidden">
        <h2 className="heading border-b border-night-700 px-5 py-4 text-xl font-bold">Dropped by</h2>
        {item.droppedBy.length === 0 ? (
          <p className="px-5 py-8 text-center text-mist-dim">No monster drops this item.</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left font-display text-xs tracking-wider text-accent uppercase">
                <th className="px-5 py-3">Monster</th>
                <th className="px-5 py-3 text-right">Level</th>
                <th className="px-5 py-3 text-right">Quantity</th>
                <th className="px-5 py-3 text-right">Rarity</th>
              </tr>
            </thead>
            <tbody>
              {item.droppedBy.map((source, i) => (
                <tr key={`${source.monsterId}-${i}`} className="border-t border-night-800 odd:bg-night-950/40">
                  <td className="px-5 py-2">
                    <Link href={`/database/monsters/${source.monsterId}`} className="font-semibold hover:text-accent-hover">
                      {source.monster}
                    </Link>
                  </td>
                  <td className="px-5 py-2 text-right text-mist-dim">{source.combatLevel > 0 ? source.combatLevel : "-"}</td>
                  <td className="px-5 py-2 text-right">{source.quantity}</td>
                  <td className="px-5 py-2 text-right">
                    <Rarity chance={source.chance} text={source.rarity} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="panel p-4 text-center">
      <dt className="text-xs tracking-wider text-mist-dim uppercase">{label}</dt>
      <dd className="mt-1 font-display text-lg font-bold text-accent">{value}</dd>
    </div>
  );
}

function Tag({ on, children }: { on: boolean; children: React.ReactNode }) {
  return (
    <span className={`rounded border px-2 py-0.5 ${on ? "border-success/50 text-success" : "border-night-600 text-mist-dim line-through"}`}>
      {children}
    </span>
  );
}