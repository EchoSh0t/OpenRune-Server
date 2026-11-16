"use client";

import { useActionState, useState, useTransition } from "react";
import { type EnrolState, type FormState, beginEnrolment, confirmEnrolment } from "../actions";

const INITIAL: FormState = { ok: true, message: "" };

export function EnrolPanel() {
  const [enrol, setEnrol] = useState<EnrolState | null>(null);
  const [starting, startTransition] = useTransition();
  const [state, confirm, confirming] = useActionState(confirmEnrolment, INITIAL);

  if (state.ok && state.message) {
    return (
      <p role="status" className="rounded-md border border-success/40 bg-success/10 px-4 py-3 text-sm text-success">
        {state.message} You&apos;ll need a code from the app whenever you log in, in game and here.
      </p>
    );
  }

  if (!enrol?.ok || !enrol.secret || !enrol.qrSvg) {
    return (
      <div>
        <button
          type="button"
          disabled={starting}
          onClick={() => startTransition(async () => setEnrol(await beginEnrolment()))}
          className="btn-primary"
        >
          {starting ? "Starting…" : "Set up authenticator"}
        </button>
        {enrol && !enrol.ok && (
          <p role="alert" className="mt-3 text-sm text-danger">
            {enrol.message}
          </p>
        )}
      </div>
    );
  }

  const grouped = enrol.secret.match(/.{1,4}/g)?.join(" ") ?? enrol.secret;
  return (
    <div>
      <p className="text-sm text-mist-dim">
        Scan this code with an authenticator app (Google Authenticator, Authy, 1Password…), then enter the 6-digit code
        it shows to finish.
      </p>
      <div
        className="mx-auto mt-6 w-48 overflow-hidden rounded-md"
        aria-label="Authenticator QR code"
        dangerouslySetInnerHTML={{ __html: enrol.qrSvg }}
      />
      <p className="mt-4 text-center text-xs text-mist-dim">Can&apos;t scan it? Enter this key instead:</p>
      <p className="mt-1 text-center font-mono text-sm tracking-wider break-all text-mist">{grouped}</p>
      <form action={confirm} className="mt-6 space-y-4">
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
        {state.message && (
          <p role="alert" className="text-sm text-danger">
            {state.message}
          </p>
        )}
        <button type="submit" disabled={confirming} className="btn-primary w-full">
          {confirming ? "Checking…" : "Turn on authenticator"}
        </button>
      </form>
    </div>
  );
}
