import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { safeNext } from "@/lib/account-api";
import { one } from "@/lib/format";
import { SignInForm } from "./SignInForm";

export const metadata: Metadata = { title: "Sign in" };

export default function AccountLoginPage({ searchParams }: PageProps<"/account/login">) {
  return (
    <div className="mx-auto max-w-md px-4 py-20">
      <div className="panel p-8">
        <h1 className="heading text-2xl font-bold">Sign in</h1>
        <p className="mt-2 text-sm text-mist-dim">Use the same username and password as in game.</p>
        <Suspense fallback={<div className="mt-6 h-48" />}>
          {searchParams.then((params) => (
            <SignInForm next={safeNext(one(params.next))} />
          ))}
        </Suspense>
        <p className="mt-6 text-center text-sm text-mist-dim">
          No account yet?{" "}
          <Link href="/register" className="font-semibold text-star-300 hover:text-accent-hover">
            Register for free
          </Link>
        </p>
      </div>
    </div>
  );
}
