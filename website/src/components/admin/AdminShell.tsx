import Link from "next/link";
import { signOut } from "@/app/admin/actions";
import type { Staff } from "@/lib/admin-api";

const LINKS = [
  { href: "/admin", label: "Dashboard", admin: false },
  { href: "/admin/players", label: "Players", admin: false },
  { href: "/admin/search", label: "Search", admin: false },
  { href: "/admin/reports", label: "Reports", admin: false },
  { href: "/admin/bans", label: "Bans", admin: false },
  { href: "/admin/staff", label: "Staff", admin: true },
  { href: "/admin/audit", label: "Audit log", admin: true },
];

export function AdminShell({ staff, children }: { staff: Staff; children: React.ReactNode }) {
  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <div className="panel flex flex-wrap items-center gap-4 px-5 py-3">
        <span className="heading text-lg font-bold">Admin</span>
        <nav className="flex flex-wrap gap-4 text-sm font-semibold">
          {LINKS.filter((link) => staff.admin || !link.admin).map((link) => (
            <Link key={link.href} href={link.href} className="text-mist-dim hover:text-accent-hover">
              {link.label}
            </Link>
          ))}
        </nav>
        <div className="ml-auto flex items-center gap-3 text-sm">
          <span className="text-mist-dim">
            {staff.name}{" "}
            <span className="rounded border border-night-600 px-1.5 py-0.5 text-xs text-star-300">
              {staff.admin ? "Administrator" : "Moderator"}
            </span>
          </span>
          <form action={signOut}>
            <button type="submit" className="btn-secondary px-3 py-1.5 text-xs">
              Sign out
            </button>
          </form>
        </div>
      </div>
      <div className="mt-6">{children}</div>
    </div>
  );
}

export function AdminOffline() {
  return (
    <div className="mx-auto max-w-xl px-4 py-24 text-center">
      <h1 className="heading text-2xl font-bold">Game server offline</h1>
      <p className="mt-3 text-mist-dim">
        The admin area talks to the game server, which isn&apos;t answering right now. Try again once
        it&apos;s running.
      </p>
    </div>
  );
}

export function Section({
  title,
  children,
  className = "",
}: {
  title: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section className={`panel p-5 ${className}`}>
      <h2 className="heading text-lg font-bold">{title}</h2>
      <div className="mt-4">{children}</div>
    </section>
  );
}
