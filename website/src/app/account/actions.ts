"use server";

import { refresh } from "next/cache";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import QRCode from "qrcode";
import {
  ACCOUNT_CHALLENGE_COOKIE,
  ACCOUNT_COOKIE,
  ACCOUNT_MAX_AGE,
  type AuthResponse,
  CHALLENGE_MAX_AGE,
  accountCookie,
  accountFetch,
  accountToken,
  safeNext,
} from "@/lib/account-api";
import { GameApiUnavailable } from "@/lib/game-api";

export type FormState = { ok: boolean; message: string; restart?: boolean };

export type EnrolState = FormState & { secret?: string; qrSvg?: string };

const OFFLINE = "The game server is offline right now. Try again in a moment.";
const SIGNED_OUT = "Your session ended. Please sign in again.";

async function call(path: string, body?: unknown, withToken = true): Promise<{ status: number; data: AuthResponse }> {
  const token = withToken ? await accountToken() : undefined;
  const res = await accountFetch(path, { method: "POST", body, token });
  return { status: res.status, data: (await res.json()) as AuthResponse };
}

function failure(e: unknown): FormState {
  if (e instanceof GameApiUnavailable) return { ok: false, message: OFFLINE };
  throw e;
}

export async function signIn(_prev: FormState, formData: FormData): Promise<FormState> {
  const username = String(formData.get("username") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  const next = safeNext(String(formData.get("next") ?? ""));
  if (!username || !password) return { ok: false, message: "Enter your username and password." };

  let result: AuthResponse;
  try {
    result = (await call("/api/account/login", { username, password }, false)).data;
  } catch (e) {
    return failure(e);
  }
  const store = await cookies();
  if (result.token) {
    store.set(ACCOUNT_COOKIE, result.token, accountCookie(ACCOUNT_MAX_AGE));
    redirect(next);
  }
  if (result.challenge) {
    store.set(ACCOUNT_CHALLENGE_COOKIE, result.challenge, accountCookie(CHALLENGE_MAX_AGE));
    redirect(`/account/verify?${new URLSearchParams({ next })}`);
  }
  return { ok: false, message: result.message ?? "Sign-in failed." };
}

export async function verifyCode(_prev: FormState, formData: FormData): Promise<FormState> {
  const store = await cookies();
  const challenge = store.get(ACCOUNT_CHALLENGE_COOKIE)?.value;
  if (!challenge) return { ok: false, message: "Sign-in expired. Please start again.", restart: true };
  const code = String(formData.get("code") ?? "");
  const next = safeNext(String(formData.get("next") ?? ""));

  let result: AuthResponse;
  try {
    result = (await call("/api/account/verify", { challenge, code }, false)).data;
  } catch (e) {
    return failure(e);
  }
  if (!result.token) {
    if (result.restart) store.delete({ name: ACCOUNT_CHALLENGE_COOKIE, path: "/account" });
    return { ok: false, message: result.message ?? "That code isn't right.", restart: result.restart };
  }
  store.delete({ name: ACCOUNT_CHALLENGE_COOKIE, path: "/account" });
  store.set(ACCOUNT_COOKIE, result.token, accountCookie(ACCOUNT_MAX_AGE));
  redirect(next);
}

export async function signOut(): Promise<void> {
  await call("/api/account/logout").catch(() => undefined);
  (await cookies()).delete({ name: ACCOUNT_COOKIE, path: "/account" });
  redirect("/account/login");
}

export async function changePassword(_prev: FormState, formData: FormData): Promise<FormState> {
  const current = String(formData.get("current") ?? "");
  const next = String(formData.get("next") ?? "");
  if (next !== String(formData.get("confirm") ?? "")) return { ok: false, message: "The new passwords don't match." };
  return simpleCall("/api/account/password", { current, next });
}

export async function beginEnrolment(): Promise<EnrolState> {
  try {
    const { status, data } = await call("/api/account/2fa/begin");
    if (status === 401) return { ok: false, message: SIGNED_OUT };
    if (!data.ok || !data.secret || !data.otpauthUri) return { ok: false, message: data.message ?? "Couldn't start set-up." };
    const qrSvg = await QRCode.toString(data.otpauthUri, {
      type: "svg",
      margin: 1,
      color: { dark: "#05070f", light: "#ffffff" },
    });
    return { ok: true, message: "", secret: data.secret, qrSvg };
  } catch (e) {
    return failure(e);
  }
}

export async function confirmEnrolment(_prev: FormState, formData: FormData): Promise<FormState> {
  return simpleCall("/api/account/2fa/confirm", { code: String(formData.get("code") ?? "") });
}

export async function disableTwoFactor(_prev: FormState, formData: FormData): Promise<FormState> {
  return simpleCall("/api/account/2fa/disable", { code: String(formData.get("code") ?? "") });
}

export async function unlinkDiscord(): Promise<void> {
  await call("/api/account/discord/unlink").catch(() => undefined);
  refresh();
}

export async function submitReport(_prev: FormState, formData: FormData): Promise<FormState> {
  return simpleCall(
    "/api/reports",
    {
      offender: String(formData.get("offender") ?? "").trim(),
      category: String(formData.get("category") ?? ""),
      details: String(formData.get("details") ?? "").trim(),
    },
    false,
  );
}

async function simpleCall(path: string, body: unknown, refreshAfter = true): Promise<FormState> {
  try {
    const { status, data } = await call(path, body);
    if (status === 401) return { ok: false, message: SIGNED_OUT };
    if (data.ok && refreshAfter) refresh();
    return { ok: data.ok, message: data.message ?? (data.ok ? "Done." : "Something went wrong.") };
  } catch (e) {
    return failure(e);
  }
}
