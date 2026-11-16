"use client";

import { useEffect, useState } from "react";

const REFRESH_MS = 30_000;

type Status = { online: true; total: number } | { online: false } | null;

export function OnlineBadge({ large = false }: { large?: boolean }) {
  const [status, setStatus] = useState<Status>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const res = await fetch("/api/online", { cache: "no-store" });
        const data = res.ok ? await res.json() : null;
        if (!cancelled) setStatus(data ? { online: true, total: data.total } : { online: false });
      } catch {
        if (!cancelled) setStatus({ online: false });
      }
    };
    load();
    const id = setInterval(load, REFRESH_MS);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, []);

  const dot =
    status === null ? "bg-night-600" : status.online ? "bg-success shadow-[0_0_8px_#00ff00]" : "bg-danger";
  const label =
    status === null
      ? "Checking..."
      : status.online
        ? `${status.total.toLocaleString()} ${status.total === 1 ? "player" : "players"} online`
        : "Server offline";

  return (
    <span
      className={`inline-flex items-center gap-2 rounded-full border border-night-700 bg-night-900/80 ${
        large ? "px-4 py-2 text-base" : "px-3 py-1 text-xs"
      } font-semibold text-mist`}
      aria-live="polite"
    >
      <span className={`size-2 rounded-full ${dot}`} />
      {label}
    </span>
  );
}
