import { SITE } from "@/lib/site";

export function DiscordBand() {
  if (!SITE.discordUrl) return null;
  return (
    <section className="border-y border-[#5865F2]/40 bg-linear-to-r from-[#5865F2]/20 via-[#5865F2]/10 to-transparent">
      <div className="mx-auto flex max-w-6xl flex-col items-start gap-4 px-4 py-8 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="heading text-2xl font-bold">Join the community</p>
          <p className="mt-1 text-mist-dim">
            Find a clan, report bugs, follow development and chat with other adventurers.
          </p>
        </div>
        <a
          href={SITE.discordUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="btn border border-[#4752c4] bg-[#5865F2] text-white hover:bg-[#4752c4]"
        >
          Join our Discord
        </a>
      </div>
    </section>
  );
}
