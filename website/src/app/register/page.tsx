import type { Metadata } from "next";
import { PageHeader } from "@/components/PageHeader";
import { SITE } from "@/lib/site";
import { RegisterForm } from "./RegisterForm";

export const metadata: Metadata = { title: "Create account" };

export default function RegisterPage() {
  return (
    <>
      <PageHeader title="Create your account">
        One account is all you need to play {SITE.name}. It&apos;s free and takes a few seconds.
      </PageHeader>
      <div className="mx-auto grid max-w-6xl gap-10 px-4 py-12 lg:grid-cols-[1fr_22rem]">
        <RegisterForm turnstileSiteKey={process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY || undefined} />
        <aside className="space-y-5">
          <div className="panel p-5">
            <h2 className="heading text-lg font-bold">Good to know</h2>
            <ul className="mt-3 space-y-2 text-sm text-mist-dim">
              <li>Your username is what you type on the game&apos;s login screen.</li>
              <li>Names are not case sensitive: &quot;Zezima&quot; and &quot;zezima&quot; are the same account.</li>
              <li>Names that only differ by spaces count as the same name.</li>
              <li>Staff-looking names (containing &quot;mod&quot;, &quot;jagex&quot; and similar) are blocked.</li>
            </ul>
          </div>
          <div className="panel p-5">
            <h2 className="heading text-lg font-bold">Keep your account safe</h2>
            <p className="mt-3 text-sm text-mist-dim">
              Staff will never ask for your password. Use a password you don&apos;t use anywhere else.
            </p>
          </div>
        </aside>
      </div>
    </>
  );
}
