import type { Metadata } from "next";
import { SignInForm } from "./SignInForm";

export const metadata: Metadata = { title: "Staff sign in" };

export default function AdminLoginPage() {
  return (
    <div className="mx-auto max-w-md px-4 py-20">
      <div className="panel p-8">
        <h1 className="heading text-2xl font-bold">Staff sign in</h1>
        <p className="mt-2 text-sm text-mist-dim">
          Use your game account. You&apos;ll need your authenticator app for the next step.
        </p>
        <SignInForm />
      </div>
    </div>
  );
}
