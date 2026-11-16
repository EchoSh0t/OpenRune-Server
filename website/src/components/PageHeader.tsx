import type { ReactNode } from "react";

export function PageHeader({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="border-b border-night-700 bg-linear-to-b from-night-900 to-transparent">
      <div className="mx-auto max-w-6xl px-4 py-12">
        <h1 className="heading text-4xl font-black sm:text-5xl">{title}</h1>
        {children && <div className="mt-3 max-w-2xl text-lg text-mist-dim">{children}</div>}
      </div>
    </div>
  );
}
