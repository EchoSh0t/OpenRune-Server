import Link from "next/link";
import { PageHeader } from "./PageHeader";

export function ComingSoon({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <>
      <PageHeader title={title}>{children}</PageHeader>
      <div className="mx-auto max-w-3xl px-4 py-16">
        <div className="panel flex flex-col items-center gap-5 p-10 text-center">
          <p className="heading text-3xl font-bold">Coming soon</p>
          <p className="max-w-md text-mist-dim">
            We&apos;re still working on this. Follow the news for updates, and in the meantime there&apos;s plenty to
            do in game.
          </p>
          <div className="flex flex-col gap-3 sm:flex-row">
            <Link href="/news" className="btn-secondary">
              Read the news
            </Link>
            <Link href="/play" className="btn-primary">
              Play now
            </Link>
          </div>
        </div>
      </div>
    </>
  );
}
