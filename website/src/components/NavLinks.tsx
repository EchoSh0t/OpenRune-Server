"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";

type Item = { href: string; label: string };

export function NavLinks({ items }: { items: readonly Item[] }) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  const isActive = (href: string) => (href === "/" ? pathname === "/" : pathname.startsWith(href));

  const links = items.map((item) => (
    <Link
      key={item.href}
      href={item.href}
      onClick={() => setOpen(false)}
      className={`font-display text-sm font-semibold tracking-wide transition ${
        isActive(item.href) ? "text-accent-hover" : "text-mist-dim hover:text-star-300"
      }`}
    >
      {item.label}
    </Link>
  ));

  return (
    <>
      <nav className="hidden items-center gap-5 md:flex">{links}</nav>
      <button
        type="button"
        className="btn-secondary order-last px-3 py-2 md:hidden"
        aria-expanded={open}
        aria-label="Toggle menu"
        onClick={() => setOpen((v) => !v)}
      >
        Menu
      </button>
      {open && (
        <nav className="absolute inset-x-0 top-full flex flex-col gap-4 border-b border-night-700 bg-night-950 px-6 py-5 md:hidden">
          {links}
          <Link
            href="/account"
            onClick={() => setOpen(false)}
            className="font-display text-sm font-semibold tracking-wide text-mist-dim hover:text-star-300"
          >
            Account
          </Link>
          <Link href="/register" onClick={() => setOpen(false)} className="btn-primary">
            Register
          </Link>
        </nav>
      )}
    </>
  );
}
