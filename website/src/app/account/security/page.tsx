import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { AccountForm } from "@/components/account/AccountForm";
import { requireAccount } from "@/lib/account-api";
import { disableTwoFactor } from "../actions";
import { EnrolPanel } from "./EnrolPanel";

export const metadata: Metadata = { title: "Authenticator" };

export default function SecurityPage() {
  return (
    <div className="mx-auto max-w-md px-4 py-12">
      <Link href="/account" className="text-sm font-semibold text-star-300 hover:text-accent-hover">
        &larr; My account
      </Link>
      <Suspense fallback={<div className="panel mt-6 h-72 animate-pulse" />}>
        <Security />
      </Suspense>
    </div>
  );
}

async function Security() {
  const { account } = await requireAccount("/account/security");

  return (
    <div className="panel mt-6 p-8">
      <h1 className="heading text-2xl font-bold">Authenticator</h1>
      {account.twoFactor ? (
        <>
          <p className="mt-2 text-sm text-mist-dim">
            Your authenticator is <strong className="text-success">on</strong>. To remove it, enter a current code from the
            app. Without it, anyone with your password can log in.
          </p>
          <AccountForm action={disableTwoFactor} submit="Remove authenticator" pendingLabel="Checking…" danger className="mt-6">
            <input
              name="code"
              inputMode="numeric"
              autoComplete="one-time-code"
              pattern="[0-9 ]{6,7}"
              maxLength={7}
              required
              placeholder="123 456"
              aria-label="Authenticator code"
              className="field text-center font-mono text-2xl tracking-[0.3em]"
            />
          </AccountForm>
        </>
      ) : (
        <>
          <p className="mt-2 mb-6 text-sm text-mist-dim">
            An authenticator app adds a 6-digit code to every login, so a stolen password alone can&apos;t get into your
            account. It protects both the game and this website.
          </p>
          <EnrolPanel />
        </>
      )}
    </div>
  );
}
