"use client";

import Link from "next/link";
import Script from "next/script";
import { useActionState, useEffect, useState } from "react";
import { PASSWORD_MAX, USERNAME_MAX, passwordProblem, usernameProblem } from "@/lib/validation";
import { register, type RegisterState } from "./actions";

const INITIAL: RegisterState = { status: "idle", message: "" };

export function RegisterForm({ turnstileSiteKey }: { turnstileSiteKey?: string }) {
  const [state, action, pending] = useActionState(register, INITIAL);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [terms, setTerms] = useState(false);
  const [touched, setTouched] = useState<Record<string, boolean>>({});

  useEffect(() => {
    if (state.status === "error") {
      (window as { turnstile?: { reset: () => void } }).turnstile?.reset();
    }
  }, [state]);

  if (state.status === "success") {
    return (
      <div className="panel p-8 text-center">
        <p className="heading text-3xl font-bold">Welcome, {state.accountName}!</p>
        <p className="mt-4 text-mist-dim">
          Your account is ready. Log in to the game with the username{" "}
          <strong className="text-mist">{state.accountName}</strong> and the password you just chose.
        </p>
        <Link href="/play" className="btn-primary mt-8">
          How to play
        </Link>
      </div>
    );
  }

  const touch = (field: string) => setTouched((t) => ({ ...t, [field]: true }));
  const live = {
    username: touched.username ? usernameProblem(username) : null,
    password: touched.password ? passwordProblem(username, password) : null,
    confirm: touched.confirm && confirm !== password ? "The passwords don't match." : null,
  };
  const serverError = (field: RegisterState["field"]) =>
    state.status === "error" && state.field === field ? state.message : null;
  const errorFor = (field: "username" | "password" | "confirm") => live[field] ?? serverError(field);

  return (
    <form action={action} className="panel space-y-5 p-6 sm:p-8" noValidate>
      {turnstileSiteKey && (
        <Script src="https://challenges.cloudflare.com/turnstile/v0/api.js" strategy="afterInteractive" />
      )}

      <Field label="Username" hint={`Up to ${USERNAME_MAX} letters, numbers and spaces. This is your login name.`} error={errorFor("username")}>
        <input
          name="username"
          className="field"
          autoComplete="username"
          maxLength={USERNAME_MAX + 8}
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          onBlur={() => touch("username")}
          required
        />
      </Field>

      <Field label="Password" hint="At least 8 characters. Don't reuse a password from another game." error={errorFor("password")}>
        <input
          name="password"
          type="password"
          className="field"
          autoComplete="new-password"
          maxLength={PASSWORD_MAX}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          onBlur={() => touch("password")}
          required
        />
      </Field>

      <Field label="Confirm password" error={errorFor("confirm")}>
        <input
          name="confirm"
          type="password"
          className="field"
          autoComplete="new-password"
          maxLength={PASSWORD_MAX}
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          onBlur={() => touch("confirm")}
          required
        />
      </Field>

      <Field label="Email (optional)" hint="Only used to help you recover your account." error={serverError("email")}>
        <input name="email" type="email" className="field" autoComplete="email" defaultValue={state.values?.email} />
      </Field>

      <div>
        <label className="flex items-start gap-3 text-sm text-mist-dim">
          <input
            name="terms"
            type="checkbox"
            className="mt-0.5 size-4 accent-star-500"
            checked={terms}
            onChange={(e) => setTerms(e.target.checked)}
            required
          />
          <span>
            I&apos;ll follow the server rules: no botting, scamming, real-world trading or abusing bugs.
          </span>
        </label>
        <ErrorText message={serverError("terms")} />
      </div>

      {turnstileSiteKey && (
        <div>
          <div className="cf-turnstile" data-sitekey={turnstileSiteKey} data-theme="dark" />
          <ErrorText message={serverError("captcha")} />
        </div>
      )}

      {state.status === "error" && !state.field && (
        <p role="alert" className="rounded-md border border-danger/50 bg-danger/10 px-4 py-3 text-sm text-danger">
          {state.message}
        </p>
      )}

      <button type="submit" className="btn-primary w-full py-4 text-base" disabled={pending}>
        {pending ? "Creating account..." : "Create account"}
      </button>
    </form>
  );
}

function Field({
  label,
  hint,
  error,
  children,
}: {
  label: string;
  hint?: string;
  error?: string | null;
  children: React.ReactNode;
}) {
  return (
    <label className="block">
      <span className="mb-1.5 block font-display text-sm font-bold tracking-wide text-accent">{label}</span>
      {children}
      {error ? <ErrorText message={error} /> : hint && <span className="mt-1.5 block text-xs text-mist-dim">{hint}</span>}
    </label>
  );
}

function ErrorText({ message }: { message?: string | null }) {
  if (!message) return null;
  return (
    <span role="alert" className="mt-1.5 block text-xs font-semibold text-danger">
      {message}
    </span>
  );
}
