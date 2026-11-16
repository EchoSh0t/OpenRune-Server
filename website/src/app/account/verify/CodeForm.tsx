"use client";

import Link from "next/link";
import { useActionState } from "react";
import { verifyCode, type FormState } from "../actions";

const INITIAL: FormState = { ok: true, message: "" };

export function CodeForm({ next }: { next: string }) {
  const [state, action, pending] = useActionState(verifyCode, INITIAL);

  if (state.restart) {
    return (
      <div className="mt-6 text-center">
        <p role="alert" className="text-sm text-danger">
          {state.message}
        </p>
        <Link href="/account/login" className="btn-primary mt-4">
          Start again
        </Link>
      </div>
    );
  }

  return (
    <form action={action} className="mt-6 space-y-4">
      <input type="hidden" name="next" value={next} />
      <input
        name="code"
        inputMode="numeric"
        autoComplete="one-time-code"
        pattern="[0-9 ]{6,7}"
        maxLength={7}
        required
        autoFocus
        placeholder="123 456"
        aria-label="Authenticator code"
        className="field text-center font-mono text-2xl tracking-[0.3em]"
      />
      {state.message && (
        <p role="alert" className="text-sm text-danger">
          {state.message}
        </p>
      )}
      <button type="submit" disabled={pending} className="btn-primary w-full">
        {pending ? "Checking…" : "Sign in"}
      </button>
    </form>
  );
}
