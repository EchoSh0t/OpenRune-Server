import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { AccountForm, Field } from "@/components/account/AccountForm";
import { type AccountView, requireAccount } from "@/lib/account-api";
import { formatNumber, one } from "@/lib/format";
import { GameApiUnavailable } from "@/lib/game-api";
import { changePassword, signOut, unlinkDiscord } from "./actions";

export const metadata: Metadata = { title: "My account" };

const DISCORD_NOTICES: Record<string, { ok: boolean; text: string }> = {
  linked: { ok: true, text: "Your Discord account is now linked." },
  taken: { ok: false, text: "That Discord account is already linked to another game account." },
  failed: { ok: false, text: "Discord linking didn't work. Please try again." },
  disabled: { ok: false, text: "Discord linking isn't set up on this site yet." },
};

export default function AccountPage({ searchParams }: PageProps<"/account">) {
  return (
    <div className="mx-auto max-w-5xl px-4 py-12">
      <Suspense fallback={<div className="panel h-96 animate-pulse" />}>
        {searchParams.then((params) => (
          <Account discord={one(params.discord)} />
        ))}
      </Suspense>
    </div>
  );
}

async function Account({ discord }: { discord?: string }) {
  let account: AccountView;
  try {
    ({ account } = await requireAccount());
  } catch (e) {
    if (e instanceof GameApiUnavailable) return <Offline />;
    throw e;
  }
  const notice = discord ? DISCORD_NOTICES[discord] : undefined;
  const discordEnabled = Boolean(process.env.DISCORD_CLIENT_ID && process.env.DISCORD_CLIENT_SECRET);

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center gap-4">
        <h1 className="heading text-4xl font-black">{account.name}</h1>
        <div className="ml-auto flex flex-wrap gap-3">
          {account.staff && (
            <Link href="/admin" className="btn-primary px-4 py-2">
              Admin area
            </Link>
          )}
          <form action={signOut}>
          <button type="submit" className="btn-secondary px-4 py-2">
              Sign out
            </button>
          </form>
        </div>
      </div>

      {notice && (
        <p
          role="status"
          className={`rounded-md border px-4 py-3 text-sm ${
            notice.ok ? "border-success/40 bg-success/10 text-success" : "border-danger/50 bg-danger/10 text-danger"
          }`}
        >
          {notice.text}
        </p>
      )}

      <div className="grid gap-8 lg:grid-cols-2">
        <Section title="Account">
          <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
            <dt className="text-mist-dim">Username</dt>
            <dd>{account.name}</dd>
            <dt className="text-mist-dim">Email</dt>
            <dd>{account.email ?? "None"}</dd>
            <dt className="text-mist-dim">Created</dt>
            <dd>{account.createdAt ?? "Unknown"}</dd>
            <dt className="text-mist-dim">Authenticator</dt>
            <dd className={account.twoFactor ? "text-success" : "text-danger"}>{account.twoFactor ? "On" : "Off"}</dd>
          </dl>
          <div className="mt-5 flex flex-wrap gap-3">
            <Link href="/account/security" className="btn-secondary px-4 py-2">
              {account.twoFactor ? "Manage authenticator" : "Set up authenticator"}
            </Link>
            <Link href="/account/report" className="btn-secondary px-4 py-2">
              Report a player
            </Link>
          </div>
        </Section>

        <Section title="Characters">
          {account.characters.length === 0 ? (
            <p className="text-sm text-mist-dim">Log in to the game to create your character.</p>
          ) : (
            <ul className="divide-y divide-night-800 text-sm">
              {account.characters.map((c) => (
                <li key={c.name} className="flex flex-wrap items-center gap-3 py-2">
                  <Link href={`/players/${encodeURIComponent(c.name)}`} className="font-semibold hover:text-accent-hover">
                    {c.name}
                  </Link>
                  <span className="text-mist-dim">Total level {formatNumber(c.totalLevel)}</span>
                  {c.members && <Tag tone="info">Member</Tag>}
                  {c.banned && <Tag tone="bad">Banned</Tag>}
                  {c.muted && <Tag tone="bad">Muted</Tag>}
                </li>
              ))}
            </ul>
          )}
        </Section>

        <Section title="Change password">
          <AccountForm action={changePassword} submit="Change password">
            <Field label="Current password">
              <input name="current" type="password" autoComplete="current-password" required maxLength={64} className="field" />
            </Field>
            <Field label="New password" hint="At least 8 characters. Changing it signs you out everywhere else on the website.">
              <input name="next" type="password" autoComplete="new-password" required minLength={8} maxLength={64} className="field" />
            </Field>
            <Field label="Confirm new password">
              <input name="confirm" type="password" autoComplete="new-password" required maxLength={64} className="field" />
            </Field>
          </AccountForm>
        </Section>

        <Section title="Discord">
          {account.discordLinked ? (
            <>
              <p className="text-sm text-mist-dim">Your Discord account is linked.</p>
              <form action={unlinkDiscord} className="mt-4">
                <button type="submit" className="btn-danger px-4 py-2">
                  Unlink Discord
                </button>
              </form>
            </>
          ) : (
            <>
              <p className="text-sm text-mist-dim">
                Link your Discord account to get your in-game name and roles on our Discord server.
              </p>
              {discordEnabled ? (
                <a href="/account/discord/start" className="btn-primary mt-4">
                  Link Discord
                </a>
              ) : (
                <p className="mt-4 text-sm text-mist-dim">Discord linking is coming soon.</p>
              )}
            </>
          )}
        </Section>
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="panel p-6">
      <h2 className="heading text-xl font-bold">{title}</h2>
      <div className="mt-4">{children}</div>
    </section>
  );
}

const TONES = { info: "border-star-500/50 text-star-300", bad: "border-danger/50 text-danger" };

function Tag({ tone, children }: { tone: keyof typeof TONES; children: React.ReactNode }) {
  return <span className={`rounded border px-2 py-0.5 text-xs font-semibold ${TONES[tone]}`}>{children}</span>;
}

function Offline() {
  return (
    <div className="panel px-5 py-16 text-center">
      <p className="heading text-2xl font-bold">Accounts unavailable</p>
      <p className="mt-3 text-mist-dim">The game server can&apos;t be reached right now. Please try again shortly.</p>
    </div>
  );
}
