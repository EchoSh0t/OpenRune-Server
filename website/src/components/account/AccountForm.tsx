"use client";

import { useActionState, useEffect, useRef } from "react";
import type { FormState } from "@/app/account/actions";

const INITIAL: FormState = { ok: true, message: "" };

/** A form bound to one account server action, showing its result underneath. */
export function AccountForm({
  action,
  submit,
  pendingLabel = "Saving…",
  danger = false,
  className = "",
  children,
}: {
  action: (prev: FormState, formData: FormData) => Promise<FormState>;
  submit: string;
  pendingLabel?: string;
  danger?: boolean;
  className?: string;
  children: React.ReactNode;
}) {
  const [state, formAction, pending] = useActionState(action, INITIAL);
  const form = useRef<HTMLFormElement>(null);

  useEffect(() => {
    if (state.ok && state.message) form.current?.reset();
  }, [state]);

  return (
    <form ref={form} action={formAction} className={`space-y-4 ${className}`}>
      {children}
      {state.message && (
        <p role="status" className={`text-sm ${state.ok ? "text-success" : "text-danger"}`}>
          {state.message}
        </p>
      )}
      <button type="submit" disabled={pending} className={danger ? "btn-danger" : "btn-primary"}>
        {pending ? pendingLabel : submit}
      </button>
    </form>
  );
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 block font-display text-sm font-bold tracking-wide text-accent">{label}</span>
      {children}
      {hint && <span className="mt-1.5 block text-xs text-mist-dim">{hint}</span>}
    </label>
  );
}
