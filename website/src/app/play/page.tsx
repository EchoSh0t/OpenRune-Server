import type { Metadata } from "next";
import Link from "next/link";
import { PageHeader } from "@/components/PageHeader";
import { SITE } from "@/lib/site";

export const metadata: Metadata = { title: "Play" };

const DOWNLOADS = [
  {
    platform: "Windows",
    detail: "Installer for Windows 10 and 11",
    url: process.env.NEXT_PUBLIC_DOWNLOAD_WINDOWS_URL,
  },
  {
    platform: "macOS",
    detail: "For Apple Silicon and Intel Macs",
    url: process.env.NEXT_PUBLIC_DOWNLOAD_MACOS_URL,
  },
  {
    platform: "Linux",
    detail: "AppImage for most distributions",
    url: process.env.NEXT_PUBLIC_DOWNLOAD_LINUX_URL,
  },
  {
    platform: "Jar",
    detail: "Cross-platform, requires Java installed",
    url: process.env.NEXT_PUBLIC_DOWNLOAD_JAR_URL,
  },
];

export default function PlayPage() {
  return (
    <>
      <PageHeader title={`Play ${SITE.name}`}>
        Download the launcher for your system, log in with your account and you&apos;re in.
      </PageHeader>
      <div className="mx-auto max-w-4xl space-y-6 px-4 py-12">
        <div className="grid gap-4 sm:grid-cols-2">
          {DOWNLOADS.map((d) => (
            <DownloadCard key={d.platform} {...d} />
          ))}
        </div>

        <section className="panel prose-osrs p-6">
          <h2 className="heading mt-0 text-xl font-bold">Don&apos;t have an account yet?</h2>
          <p>
            Accounts are created on this website. <Link href="/register">Register here</Link>, then log in to the
            client with the same username and password.
          </p>
        </section>

        <div className="panel border-danger/40 p-5 text-sm text-mist-dim">
          <p className="font-display font-bold text-danger">Stay safe</p>
          <p className="mt-2">
            Only download the client from this page. Third-party clients can steal your account, and staff will
            never ask for your password.
          </p>
        </div>
      </div>
    </>
  );
}

function DownloadCard({ platform, detail, url }: { platform: string; detail: string; url?: string }) {
  return (
    <section className="panel flex flex-col gap-4 p-6">
      <div>
        <h2 className="heading text-xl font-bold">{platform}</h2>
        <p className="mt-1 text-sm text-mist-dim">{detail}</p>
      </div>
      {url ? (
        <a href={url} className="btn-primary mt-auto" download>
          Download for {platform}
        </a>
      ) : (
        <span className="btn-secondary mt-auto cursor-not-allowed opacity-60" aria-disabled="true">
          Coming soon
        </span>
      )}
    </section>
  );
}
