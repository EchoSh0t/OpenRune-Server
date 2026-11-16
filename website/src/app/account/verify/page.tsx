import type { Metadata } from "next";
import { Suspense } from "react";
import { safeNext } from "@/lib/account-api";
import { one } from "@/lib/format";
import { CodeForm } from "./CodeForm";

export const metadata: Metadata = { title: "Authenticator" };

export default function AccountVerifyPage({ searchParams }: PageProps<"/account/verify">) {
  return (
    <div className="mx-auto max-w-md px-4 py-20">
      <div className="panel p-8">
        <h1 className="heading text-2xl font-bold">Authenticator code</h1>
        <p className="mt-2 text-sm text-mist-dim">Enter the 6-digit code from your authenticator app.</p>
        <Suspense fallback={<div className="mt-6 h-32" />}>
          {searchParams.then((params) => (
            <CodeForm next={safeNext(one(params.next))} />
          ))}
        </Suspense>
      </div>
    </div>
  );
}
