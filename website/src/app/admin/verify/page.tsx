import type { Metadata } from "next";
import { cookies } from "next/headers";
import Link from "next/link";
import QRCode from "qrcode";
import { Suspense } from "react";
import { AdminOffline } from "@/components/admin/AdminShell";
import { AdminApiUnavailable, CHALLENGE_COOKIE, type Challenge, adminFetch } from "@/lib/admin-api";
import { CodeForm } from "./CodeForm";

export const metadata: Metadata = { title: "Authenticator" };

export default function VerifyPage() {
  return (
    <div className="mx-auto max-w-md px-4 py-20">
      <Suspense fallback={<div className="panel h-64 animate-pulse" />}>
        <Verify />
      </Suspense>
    </div>
  );
}

async function Verify() {
  const id = (await cookies()).get(CHALLENGE_COOKIE)?.value;
  if (!id) return <Expired />;

  let challenge: Challenge | null;
  try {
    const res = await adminFetch(`/api/staff/challenge?${new URLSearchParams({ id })}`);
    challenge = res.ok ? ((await res.json()) as Challenge) : null;
  } catch (e) {
    if (e instanceof AdminApiUnavailable) return <AdminOffline />;
    throw e;
  }
  if (!challenge) return <Expired />;

  if (!challenge.enrol || !challenge.secret || !challenge.otpauthUri) {
    return (
      <div className="panel p-8">
        <h1 className="heading text-2xl font-bold">Authenticator code</h1>
        <p className="mt-2 text-sm text-mist-dim">
          Enter the 6-digit code from your authenticator app for <strong>{challenge.accountName}</strong>.
        </p>
        <CodeForm />
      </div>
    );
  }

  const qr = await QRCode.toString(challenge.otpauthUri, {
    type: "svg",
    margin: 1,
    color: { dark: "#05070f", light: "#ffffff" },
  });
  const grouped = challenge.secret.match(/.{1,4}/g)?.join(" ") ?? challenge.secret;

  return (
    <div className="panel p-8">
      <h1 className="heading text-2xl font-bold">Set up your authenticator</h1>
      <p className="mt-2 text-sm text-mist-dim">
        Staff accounts need two-factor sign-in. Scan this code with an authenticator app (Google
        Authenticator, Authy, 1Password…), then enter the 6-digit code it shows.
      </p>
      <div
        className="mx-auto mt-6 w-48 overflow-hidden rounded-md"
        aria-label="Authenticator QR code"
        dangerouslySetInnerHTML={{ __html: qr }}
      />
      <p className="mt-4 text-center text-xs text-mist-dim">Can&apos;t scan it? Enter this key instead:</p>
      <p className="mt-1 text-center font-mono text-sm tracking-wider break-all text-mist">{grouped}</p>
      <p className="mt-4 rounded-md border border-night-600 bg-night-950/60 p-3 text-xs text-mist-dim">
        This also turns on the authenticator for <strong>{challenge.accountName}</strong>&apos;s in-game
        login, so keep the app safe.
      </p>
      <CodeForm />
    </div>
  );
}

function Expired() {
  return (
    <div className="panel p-8 text-center">
      <h1 className="heading text-2xl font-bold">Sign-in expired</h1>
      <p className="mt-2 text-sm text-mist-dim">Sign-in codes are only valid for a few minutes.</p>
      <Link href="/admin/login" className="btn-primary mt-6">
        Start again
      </Link>
    </div>
  );
}
