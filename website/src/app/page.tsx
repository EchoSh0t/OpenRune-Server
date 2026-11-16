import Image from "next/image";
import Link from "next/link";
import { NewsCard } from "@/components/NewsCard";
import { OnlineBadge } from "@/components/OnlineBadge";
import { getAllPosts } from "@/lib/news";
import { getAllMilestones } from "@/lib/roadmap";
import { roadmapProgress } from "@/lib/roadmap-shared";
import { SITE } from "@/lib/site";

const FEATURES = [
  {
    title: "Authentic Old School",
    body: "Built against OSRS revision 240. Movement, timings, XP rates and drop tables follow the live game, down to the tick.",
  },
  {
    title: "Skills & training",
    body: "Train the skills you know, with every Agility rooftop course, Marks of Grace and the Graceful outfit already in.",
  },
  {
    title: "Quests & bosses",
    body: "Quests, minigames and boss encounters are added every update. Check the news to see what has just shipped.",
  },
  {
    title: "Game modes",
    body: "Main game, leagues and Gridmaster worlds, each with its own saves. Try something new without risking your main.",
  },
  {
    title: "Fair & free",
    body: "No pay-to-win. Create an account, download the client and play. That's it.",
  },
  {
    title: "Built by players",
    body: "Run by long-time OSRS players who care about getting the details right. Community suggestions shape what comes next.",
  },
];

const STEPS = [
  { title: "Create your account", body: "Pick a username and password on the register page.", href: "/register" },
  { title: "Get the client", body: "Download a client and point it at our server.", href: "/play" },
  { title: "Log in and play", body: "Use the same username and password in game.", href: "/play" },
];

export default function Home() {
  const posts = getAllPosts().slice(0, 3);
  const milestones = getAllMilestones();
  const roadmap = roadmapProgress(milestones);
  const spotlight = milestones.find((m) => m.status === "in-progress") ?? milestones.find((m) => m.status === "planned");

  return (
    <>
      <section className="relative overflow-hidden border-b border-night-700">
        <div
          aria-hidden
          className="absolute inset-0 bg-cover bg-center opacity-60"
          style={{
            backgroundImage:
              "radial-gradient(ellipse at 50% 120%, rgb(116 162 242 / 0.3), transparent 55%), radial-gradient(ellipse at 20% 0%, rgb(54 67 109 / 0.6), transparent 50%), url('/hero.jpg')",
          }}
        />
        <div aria-hidden className="absolute inset-0 bg-linear-to-b from-night-950/30 via-night-950/60 to-night-950" />
        <div className="relative mx-auto flex max-w-6xl flex-col items-center px-4 py-28 text-center sm:py-36">
          <OnlineBadge large />
          <h1 className="mt-8">
            <Image
              src="/elysium-logo.png"
              alt={SITE.name}
              width={1200}
              height={758}
              priority
              className="h-auto w-full max-w-xl"
            />
          </h1>
          <p className="mt-4 font-display text-xl text-star-300 sm:text-2xl">{SITE.tagline}</p>
          <p className="mt-6 max-w-2xl text-lg text-mist-dim">
            A free Old School RuneScape server, true to the game you remember. Create your
            account in seconds and jump straight into Gielinor.
          </p>
          <div className="mt-10 flex flex-col gap-4 sm:flex-row">
            <Link href="/register" className="btn-primary px-10 py-4 text-base">
              Create account
            </Link>
            <Link href="/play" className="btn-secondary px-10 py-4 text-base">
              Play now
            </Link>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-4 pt-20">
        <div className="grid gap-10 md:grid-cols-[1.1fr_1fr] md:items-center">
          <div>
            <h2 className="heading text-3xl font-bold">What is {SITE.name}?</h2>
            <div className="mt-4 space-y-4 text-mist-dim">
              <p>
                {SITE.name} is an Old School RuneScape private server that aims to recreate the real game as
                closely as possible: the same map, items, NPCs, combat formulas and skilling.
              </p>
              <p>
                Content is added steadily, with each piece checked against live OSRS. Where something still
                differs, we say so in the patch notes.
              </p>
            </div>
          </div>
          <ol className="grid gap-3">
            {STEPS.map((step, i) => (
              <li key={step.title}>
                <Link href={step.href} className="panel flex items-center gap-4 p-4 transition hover:border-star-700">
                  <span className="grid size-10 shrink-0 place-items-center rounded-full border border-star-700 font-display text-lg font-bold text-star-300">
                    {i + 1}
                  </span>
                  <span>
                    <span className="block font-display font-bold text-mist">{step.title}</span>
                    <span className="block text-sm text-mist-dim">{step.body}</span>
                  </span>
                </Link>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-4 pt-20">
        <h2 className="heading text-center text-3xl font-bold">Why play here?</h2>
        <div className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((feature) => (
            <div key={feature.title} className="panel p-6">
              <div aria-hidden className="h-1 w-10 rounded bg-linear-to-r from-star-300 to-star-700" />
              <h3 className="mt-4 font-display text-lg font-bold text-star-300">{feature.title}</h3>
              <p className="mt-2 text-sm text-mist-dim">{feature.body}</p>
            </div>
          ))}
        </div>
      </section>

      {posts.length > 0 && (
        <section className="mx-auto max-w-6xl px-4 pt-20">
          <div className="flex items-end justify-between">
            <h2 className="heading text-3xl font-bold">Latest news</h2>
            <Link href="/news" className="text-sm font-semibold text-star-300 hover:text-accent-hover">
              All news &rarr;
            </Link>
          </div>
          <div className="mt-8 grid gap-5 md:grid-cols-3">
            {posts.map((post) => (
              <NewsCard key={post.slug} post={post} />
            ))}
          </div>
        </section>
      )}

      {milestones.length > 0 && (
        <section className="mx-auto max-w-6xl px-4 pt-20">
          <div className="panel overflow-hidden p-6 sm:p-8">
            <p className="text-xs font-semibold tracking-[0.18em] text-star-500 uppercase">
              The world we&apos;re building together
            </p>
            <div className="mt-3 flex flex-wrap items-end justify-between gap-4">
              <div>
                <h2 className="heading text-3xl font-bold">Roadmap</h2>
                <p className="mt-2 max-w-xl text-mist-dim">
                  {roadmap.completed} of {roadmap.total} milestones shipped
                  {spotlight ? ` · next up: ${spotlight.title}` : ""}.
                </p>
              </div>
              <Link href="/roadmap" className="btn-secondary px-5 py-2.5">
                Explore the journey ↗
              </Link>
            </div>
            <div className="mt-6 h-1.5 overflow-hidden border border-night-700 bg-night-900">
              <div className="h-full bg-linear-to-r from-[#3d7a52] to-[#8fcea0]" style={{ width: `${roadmap.percent}%` }} />
            </div>
          </div>
        </section>
      )}

      <section className="mx-auto max-w-6xl px-4 pt-20">
        <div className="panel flex flex-col items-center gap-6 bg-linear-to-br from-night-850 to-night-900 px-6 py-12 text-center">
          <h2 className="heading text-3xl font-bold">Your adventure starts here</h2>
          <p className="max-w-xl text-mist-dim">
            Accounts are free and take seconds to make. Your username is the name you log in with.
          </p>
          <Link href="/register" className="btn-primary px-10 py-4 text-base">
            Create your account
          </Link>
        </div>
      </section>
    </>
  );
}
