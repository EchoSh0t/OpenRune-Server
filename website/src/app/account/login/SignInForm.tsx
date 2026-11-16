"use client";

import { useActionState } from "react";
import { signIn, type FormState } from "../actions";

const INITIAL: FormState = { ok: true, message: "" };

export function SignInForm({ next }: { next: string }) {
  const [state, action, pending] = useActionState(signIn, INITIAL);

  return (
    <form action={action} className="mt-6 space-y-4">
      <input type="hidden" name="next" value={next} />
      <label className="block">
        <span className="text-sm font-semibold text-mist">Username</span>
        <input name="username" autoComplete="username" required maxLength={12} className="field mt-1" />
      </label>
      <label className="block">
        <span className="text-sm font-semibold text-mist">Password</span>
        <input
          name="password"
          type="password"
          autoComplete="current-password"
          required
          maxLength={64}
          className="field mt-1"
        />
      </label>
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
