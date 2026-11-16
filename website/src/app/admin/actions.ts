"use server";

import { refresh } from "next/cache";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import {
  AdminApiUnavailable,
  CHALLENGE_COOKIE,
  CHALLENGE_MAX_AGE,
  SESSION_COOKIE,
  SESSION_MAX_AGE,
  adminAction,
  adminFetch,
  cookieOptions,
} from "@/lib/admin-api";

export type FormState = { ok: boolean; message: string; restart?: boolean };

const OFFLINE = "The game server is offline right now. Try again in a moment.";

function failure(e: unknown): FormState {
  if (e instanceof AdminApiUnavailable) return { ok: false, message: OFFLINE };
  throw e;
}

export async function signIn(_prev: FormState, formData: FormData): Promise<FormState> {
  const username = String(formData.get("username") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  if (!username || !password) return { ok: false, message: "Enter your username and password." };

  let result: { ok: boolean; message: string; challenge?: string };
  try {
    const res = await adminFetch("/api/staff/login", { method: "POST", body: { username, password } });
    result = await res.json();
  } catch (e) {
    return failure(e);
  }
  if (!result.ok || !result.challenge) return { ok: false, message: result.message };

  (await cookies()).set(CHALLENGE_COOKIE, result.challenge, cookieOptions(CHALLENGE_MAX_AGE));
  redirect("/admin/verify");
}

export async function verifyCode(_prev: FormState, formData: FormData): Promise<FormState> {
  const store = await cookies();
  const challenge = store.get(CHALLENGE_COOKIE)?.value;
  if (!challenge) return { ok: false, message: "Sign-in expired. Please start again.", restart: true };
  const code = String(formData.get("code") ?? "");

  let result: { ok: boolean; message: string; token?: string; restart?: boolean };
  try {
    const res = await adminFetch("/api/staff/verify", { method: "POST", body: { challenge, code } });
    result = await res.json();
  } catch (e) {
    return failure(e);
  }
  if (!result.ok || !result.token) {
    if (result.restart) store.delete({ name: CHALLENGE_COOKIE, path: "/admin" });
    return { ok: false, message: result.message, restart: result.restart };
  }

  store.delete({ name: CHALLENGE_COOKIE, path: "/admin" });
  store.set(SESSION_COOKIE, result.token, cookieOptions(SESSION_MAX_AGE));
  redirect("/admin");
}

export async function signOut(): Promise<void> {
  const store = await cookies();
  const token = store.get(SESSION_COOKIE)?.value;
  if (token) {
    await adminFetch("/api/staff/logout", { method: "POST", token }).catch(() => undefined);
  }
  store.delete({ name: SESSION_COOKIE, path: "/admin" });
  redirect("/admin/login");
}

export async function findPlayer(formData: FormData): Promise<void> {
  const name = String(formData.get("name") ?? "").trim();
  if (name) redirect(`/admin/players/${encodeURIComponent(name)}`);
}

const NUMBER_FIELDS = ["count", "x", "z", "level", "seconds", "days"] as const;

/**
 * Runs one admin command. The form's hidden `type` field picks the command and the other fields
 * are passed through; the game server checks the staff member's rank before doing anything.
 */
export async function runAction(_prev: FormState, formData: FormData): Promise<FormState> {
  const body: Record<string, unknown> = { type: String(formData.get("type") ?? "") };
  for (const field of ["player", "text", "item", "mode", "reason"]) {
    const value = String(formData.get(field) ?? "").trim();
    if (value) body[field] = value;
  }
  for (const field of NUMBER_FIELDS) {
    const raw = String(formData.get(field) ?? "").trim();
    if (raw) body[field] = Number.parseInt(raw, 10);
  }
  const rate = String(formData.get("rate") ?? "").trim();
  if (rate) body.rate = Number.parseFloat(rate);
  if (formData.has("enabled")) body.enabled = formData.get("enabled") === "true";

  try {
    const result = await adminAction(body);
    refresh();
    return { ok: result.ok, message: result.message };
  } catch (e) {
    return failure(e);
  }
}

/** Staff-only database changes: notes, closing reports, ranks and authenticator resets. */
export async function runStaffAction(_prev: FormState, formData: FormData): Promise<FormState> {
  const body: Record<string, unknown> = { type: String(formData.get("type") ?? "") };
  for (const field of ["player", "text", "rights"]) {
    const value = String(formData.get(field) ?? "").trim();
    if (value) body[field] = value;
  }
  const id = String(formData.get("id") ?? "").trim();
  if (id) body.id = Number.parseInt(id, 10);

  try {
    const result = await adminAction(body, "/api/staff/action");
    refresh();
    return { ok: result.ok, message: result.message };
  } catch (e) {
    return failure(e);
  }
}

export async function searchPlayers(formData: FormData): Promise<void> {
  const q = String(formData.get("q") ?? "").trim();
  redirect(q ? `/admin/search?${new URLSearchParams({ q })}` : "/admin/search");
}
