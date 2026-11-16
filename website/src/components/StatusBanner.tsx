"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { formatCountdown } from "@/lib/format";

const REFRESH_MS = 30_000;

type Status = { maintenance: boolean; countdownSeconds: number | null; fetchedAt: number };

/** Warns every visitor about an upcoming restart or maintenance mode. */
export function StatusBanner() {
  const [status, setStatus] = useState<Status | null>(null);
  const [now, setNow] = useState(0);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const res = await fetch("/api/status", { cache: "no-store" });
        if (!res.ok) return;
        const data = await res.json();
        if (!cancelled) {
          setStatus({ maintenance: data.maintenance, countdownSeconds: data.countdownSeconds, fetchedAt: Date.now() });
        }
      } catch {
        // The online badge already shows when the server is unreachable.
      }
    };
    load();
    const poll = setInterval(load, REFRESH_MS);
    const tick = setInterval(() => setNow(Date.now()), 1000);
    return () => {
      cancelled = true;
      clearInterval(poll);
      clearInterval(tick);
    };
  }, []);

  if (!status) return null;
  const remaining =
    status.countdownSeconds === null
      ? null
      : Math.max(0, status.countdownSeconds - Math.floor(Math.max(0, now - status.fetchedAt) / 1000));
  if (remaining === null && !status.maintenance) return null;

  return (
    <div className="border-b border-danger/40 bg-danger/10 text-sm" role="status">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-center gap-x-3 gap-y-1 px-4 py-2 text-center">
        {remaining !== null ? (
          <span className="font-semibold text-mist">
            The server is restarting in {formatCountdown(remaining)}. Find somewhere safe to log out.
          </span>
        ) : (
          <span className="font-semibold text-mist">Maintenance in progress: only staff can log in right now.</span>
        )}
        <Link href="/status" className="font-semibold text-star-300 underline hover:text-accent-hover">
          Server status
        </Link>
      </div>
    </div>
  );
}
