import { redirect } from "next/navigation";

/** Old bookmark: XP gains now live on the hiscores page. */
export default async function TrackerRedirect({ searchParams }: PageProps<"/tracker">) {
  const params = await searchParams;
  const q = new URLSearchParams({ view: "gains" });
  for (const key of ["period", "skill", "page"] as const) {
    const value = Array.isArray(params[key]) ? params[key][0] : params[key];
    if (value) q.set(key, value);
  }
  redirect(`/hiscores?${q}`);
}
