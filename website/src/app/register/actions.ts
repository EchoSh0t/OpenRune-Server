"use server";

import { headers } from "next/headers";
import { GameApiUnavailable, registerAccount } from "@/lib/game-api";
import { normalizeUsername, passwordProblem, usernameProblem } from "@/lib/validation";

export type RegisterState = {
  status: "idle" | "error" | "success";
  message: string;
  field?: "username" | "password" | "confirm" | "email" | "terms" | "captcha";
  accountName?: string;
  values?: { username: string; email: string };
};

const FIELD_FOR_CODE: Record<string, RegisterState["field"]> = {
  name_invalid: "username",
  name_taken: "username",
  password_weak: "password",
  email_invalid: "email",
};

async function clientIp(): Promise<string> {
  const h = await headers();
  return (
    h.get("cf-connecting-ip") ??
    h.get("x-real-ip") ??
    h.get("x-forwarded-for")?.split(",")[0]?.trim() ??
    "unknown"
  );
}

async function verifyTurnstile(token: string, ip: string): Promise<boolean> {
  const secret = process.env.TURNSTILE_SECRET_KEY;
  if (!secret) return true;
  if (!token) return false;
  try {
    const res = await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", {
      method: "POST",
      body: new URLSearchParams({ secret, response: token, remoteip: ip }),
      signal: AbortSignal.timeout(8000),
    });
    const data = (await res.json()) as { success?: boolean };
    return data.success === true;
  } catch {
    return false;
  }
}

export async function register(_prev: RegisterState, formData: FormData): Promise<RegisterState> {
  const username = String(formData.get("username") ?? "");
  const password = String(formData.get("password") ?? "");
  const confirm = String(formData.get("confirm") ?? "");
  const email = String(formData.get("email") ?? "").trim();
  const values = { username, email };
  const fail = (field: RegisterState["field"], message: string): RegisterState => ({
    status: "error",
    field,
    message,
    values,
  });

  const nameError = usernameProblem(username);
  if (nameError) return fail("username", nameError);
  const passwordError = passwordProblem(username, password);
  if (passwordError) return fail("password", passwordError);
  if (password !== confirm) return fail("confirm", "The passwords don't match.");
  if (formData.get("terms") !== "on") return fail("terms", "Please accept the rules to continue.");

  const ip = await clientIp();
  if (!(await verifyTurnstile(String(formData.get("cf-turnstile-response") ?? ""), ip))) {
    return fail("captcha", "Please complete the human check and try again.");
  }

  try {
    const result = await registerAccount(
      { username: normalizeUsername(username), password, email: email || undefined },
      ip,
    );
    if (result.ok) {
      return { status: "success", message: result.message, accountName: result.accountName };
    }
    return fail(FIELD_FOR_CODE[result.code ?? ""], result.message);
  } catch (e) {
    const message =
      e instanceof GameApiUnavailable
        ? "The game server is offline right now. Please try again in a few minutes."
        : "Something went wrong. Please try again.";
    return fail(undefined, message);
  }
}
