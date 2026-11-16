export const SITE = {
  name: process.env.NEXT_PUBLIC_SERVER_NAME || "Elysium",
  tagline: "Old School, the way you remember it.",
  discordUrl: process.env.NEXT_PUBLIC_DISCORD_URL || "",
};

export const NAV = [
  { href: "/", label: "Home" },
  { href: "/news", label: "News" },
  { href: "/roadmap", label: "Roadmap" },
  { href: "/play", label: "Play" },
  { href: "/hiscores", label: "Hiscores" },
  { href: "/vote", label: "Vote" },
  { href: "/store", label: "Store" },
] as const;

export const FOOTER_LINKS = [
  { href: "/database", label: "Item database" },
  { href: "/compare", label: "Compare players" },
  { href: "/changelog", label: "Changelog" },
  { href: "/status", label: "Server status" },
  { href: "/account", label: "My account" },
  { href: "/account/report", label: "Report a player" },
  { href: "/register", label: "Register" },
] as const;
