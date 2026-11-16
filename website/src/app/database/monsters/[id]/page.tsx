import type { Metadata } from "next";
import { cacheLife } from "next/cache";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { ItemIcon } from "@/components/ItemIcon";
import { Rarity } from "@/components/Rarity";
import { fetchMonster, type MonsterDrop, type MonsterView } from "@/lib/game-api";

const SECTIONS = ["Always", "Pre-roll", "Main", "Tertiary"];

async function cachedMonster(id: number): Promise<MonsterView | null> {
  "use cache";
  cacheLife("hours");
  return fetchMonster(id);
}

export async function generateMetadata({ params }: PageProps<"/database/monsters/[id]">): Promise<Metadata> {
  const monster = await cachedMonster(Number((await params).id)).catch(() => null);
  return { title: monster?.name ?? "Monster" };
}

export default function MonsterPage({ params }: PageProps<"/database/monsters/[id]">) {
  return (
    <div className="mx-auto max-w-4xl px-4 py-12">
      <Link href="/database?type=monsters" className="text-sm font-semibold text-star-300 hover:text-accent-hover">
        &larr; Database
      </Link>
      <Suspense fallback={<div className="panel mt-6 h-96 animate-pulse" />}>
        {params.then(({ id }) => (
          <Monster id={Number(id)} />
        ))}
      </Suspense>
    </div>
  );
}

async function Monster({ id }: { id: number }) {
  if (!Number.isInteger(id) || id < 0) notFound();
  const monster = await cachedMonster(id).catch(() => null);
  if (!monster) notFound();

  const stats = [
    ["Hitpoints", monster.hitpoints],
    ["Attack", monster.attack],
    ["Strength", monster.strength],
    ["Defence", monster.defence],
    ["Magic", monster.magic],
    ["Ranged", monster.ranged],
  ] as const;
  const bySection = SECTIONS.map((section) => ({
    section,
    drops: monster.drops.filter((d) => d.section === section).sort((a, b) => b.chance - a.chance),
  })).filter((group) => group.drops.length > 0);

  return (
    <div className="mt-6 space-y-8">
      <div>
        <h1 className="heading text-4xl font-black">{monster.name}</h1>
        <p className="mt-1 text-mist-dim">
          {monster.combatLevel > 0 ? `Combat level ${monster.combatLevel}` : "No combat level"} &middot; Size {monster.size}
        </p>
      </div>

      <dl className="grid grid-cols-3 gap-3 sm:grid-cols-6">
        {stats.map(([label, value]) => (
          <div key={label} className="panel p-3 text-center">
            <dt className="text-xs tracking-wider text-mist-dim uppercase">{label}</dt>
            <dd className="mt-1 font-display text-lg font-bold text-accent">{value}</dd>
          </div>
        ))}
      </dl>

      {bySection.map(({ section, drops }) => (
        <section key={section} className="panel overflow-hidden">
          <h2 className="heading border-b border-night-700 px-5 py-4 text-xl font-bold">{TITLES[section] ?? section}</h2>
          <DropTable drops={drops} />
        </section>
      ))}
      <p className="text-xs text-mist-dim">
        Rarities are per kill, from the server&apos;s own drop tables. Ring of wealth and other boosts aren&apos;t included.
      </p>
    </div>
  );
}

const TITLES: Record<string, string> = {
  Always: "Always drops",
  "Pre-roll": "Pre-roll drops",
  Main: "Drops",
  Tertiary: "Tertiary drops",
};

function DropTable({ drops }: { drops: MonsterDrop[] }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="text-left font-display text-xs tracking-wider text-accent uppercase">
          <th className="px-5 py-3">Item</th>
          <th className="px-5 py-3 text-right">Quantity</th>
          <th className="px-5 py-3 text-right">Rarity</th>
        </tr>
      </thead>
      <tbody>
        {drops.map((drop, i) => (
          <tr key={`${drop.name}-${i}`} className="border-t border-night-800 odd:bg-night-950/40">
            <td className="px-5 py-1.5">
              {drop.itemId !== null ? (
                <Link href={`/database/items/${drop.itemId}`} className="flex items-center gap-3 font-semibold hover:text-accent-hover">
                  <ItemIcon id={drop.itemId} name={drop.name} />
                  {drop.name}
                </Link>
              ) : (
                <span className="flex items-center gap-3 pl-11 font-semibold text-mist-dim">{drop.name}</span>
              )}
            </td>
            <td className="px-5 py-1.5 text-right">{drop.quantity}</td>
            <td className="px-5 py-1.5 text-right">
              <Rarity chance={drop.chance} text={drop.rarity} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
