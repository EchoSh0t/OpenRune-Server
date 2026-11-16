import Image from "next/image";
import Link from "next/link";
import { Suspense } from "react";
import { NAV, SITE } from "@/lib/site";
import { NavLinks } from "./NavLinks";
import { OnlineBadge } from "./OnlineBadge";

export function SiteHeader() {
  return (
    <header className="sticky top-0 z-40 border-b border-night-700 bg-night-950/90 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center gap-6 px-4 py-3">
        <Link href="/" aria-label={SITE.name} className="flex shrink-0 items-center gap-2">
          <Image src="/elysium-star.png" alt="" width={237} height={256} priority className="h-10 w-auto" />
          <span className="heading hidden text-xl font-bold tracking-wide sm:inline">{SITE.name}</span>
        </Link>
        <Suspense fallback={<nav className="hidden md:flex" />}>
          <NavLinks items={NAV} />
        </Suspense>
        <div className="ml-auto flex items-center gap-3">
          <OnlineBadge />
          <Link
            href="/account"
            className="hidden font-display text-sm font-semibold tracking-wide text-mist-dim hover:text-star-300 lg:inline"
          >
            Account
          </Link>
          <Link href="/register" className="btn-primary hidden px-4 py-2 sm:inline-flex">
            Register
          </Link>
        </div>
      </div>
    </header>
  );
}
