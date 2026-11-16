import Link from "next/link";
import { FOOTER_LINKS, NAV, SITE } from "@/lib/site";
import { DiscordBand } from "./DiscordBand";

export function SiteFooter() {
  return (
    <footer className="mt-24">
      <DiscordBand />
      <div className="border-t border-night-700 bg-night-950">
        <div className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-10 md:flex-row md:items-start md:justify-between">
          <div className="max-w-sm">
            <p className="heading text-lg font-bold">{SITE.name}</p>
            <p className="mt-2 text-sm text-mist-dim">
              A free Old School RuneScape private server. Not affiliated with Jagex Ltd.
              RuneScape is a trademark of Jagex Ltd.
            </p>
          </div>
          <nav className="grid grid-cols-2 gap-x-10 gap-y-2 text-sm sm:grid-cols-3">
            {[...NAV, ...FOOTER_LINKS].map((item) => (
              <Link key={item.href} href={item.href} className="text-mist-dim hover:text-star-300">
                {item.label}
              </Link>
            ))}
          </nav>
        </div>
      </div>
    </footer>
  );
}
