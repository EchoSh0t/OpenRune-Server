# Elysium website

The public website for the server: landing page, news, play guide, hiscores, live player count,
and **account registration**. With `accounts.auto-register: false` in `game.yml`, registering here
is the only way to create a game account.

Also on the site:

- **Player profiles** (`/players/<name>`) and **compare** (`/compare?a=&b=`), with 30-day XP graphs.
- **Hiscores** (`/hiscores`): rankings plus an **XP gains** tab (day / week / month). The game
  server snapshots everyone's XP once a day, so gains start counting from the first snapshot
  after this is deployed. Old `/tracker` URLs redirect here.
- **Item and monster database** (`/database`, linked from the footer): drop tables and drop rates,
  read from the server's own drop tables, so it always matches the game.
- **Changelog** (`/changelog`) rendered from the repo's `CHANGELOG.md`, and a **status page**
  (`/status`). A banner appears site-wide during a restart countdown or maintenance.
- **Account area** (`/account`): sign in with the game login (and authenticator, if on), change
  password, turn the authenticator on or off, link Discord, and report players. Players can also
  report in game with `::report name, what happened`.

Built with Next.js (App Router, Cache Components) and Tailwind CSS.

## How it fits together

```
Browser ──> Next.js (this app) ──X-Api-Key──> website-api plugin (game server, :8091) ──> PostgreSQL
```

The browser never talks to the game server. Server actions and route handlers in this app call the
game server's website API (`content/other/website-api`), which:

- `POST /api/register` validates the name with the same rules Central applies at login
  (12 characters, letters/numbers/spaces, no staff-looking names, no names that collide with an
  existing account after removing case and spaces), hashes the password with Central's hasher
  (bcrypt) and inserts the account.
- `GET /api/online` counts live Central sessions per world.
- `GET /api/hiscores?skill=overall|<skill>&page=N` and `GET /api/hiscores/player?name=...` read
  the `main` world type's stats. Staff accounts and banned characters are not ranked.

Registration is rate-limited per client IP (20 attempts per 10 minutes, 3 accounts per hour).

## Running locally

1. Start the game server once (`./gradlew run`). On first start it writes
   `.data/website-api.properties` with a random `api_key`.
2. Set up the site:

   ```bash
   cd website
   cp .env.example .env.local   # paste api_key into GAME_API_KEY
   npm install
   npm run dev
   ```

3. Open http://localhost:3000 and create an account, then log in with it in game.

To make the website the only way to create accounts, set this in `game.yml`:

```yaml
accounts:
  auto-register: false
```

With it off, logging in with an unknown username returns "invalid username or password". An account
made on the website gets its first character automatically on its first login.

## Server API settings

`.data/website-api.properties`:

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Start the API with the server |
| `bind_address` | `127.0.0.1` | Interface to listen on |
| `port` | `8091` | Port |
| `api_key` | random | Shared secret; must match `GAME_API_KEY` |
| `discord_webhook_url` | empty | Discord channel webhook for level 99 and max total announcements |

The first time the server runs with a webhook set, it quietly records every 99 that already
exists, so only new ones are announced.

The server also records each character's login IP addresses (kept for 180 days) so staff can spot
alts and ban evaders.

## Discord

- **News posts:** set `DISCORD_NEWS_WEBHOOK_URL` and `SITE_URL`, then
  `npm run announce -- 2026-10-14-agility-update` (the news file name without `.md`).
- **Account linking:** create an application in the Discord developer portal, add
  `<SITE_URL>/account/discord/callback` under OAuth2 redirects, and set `DISCORD_CLIENT_ID` and
  `DISCORD_CLIENT_SECRET`. The linked id is stored in `accounts.discord_id`.

## Staff admin area (`/admin`)

Staff sign in with their game account plus an authenticator app. The site talks to a second
private API on the game server (the `web-panel` plugin, `127.0.0.1:8090`), configured in
`.data/web-panel.properties`:

| Key | Default | Meaning |
|---|---|---|
| `bind_address` | `127.0.0.1` | Interface to listen on |
| `api_key` | random | Shared secret; must match `ADMIN_API_KEY` |

Set `ADMIN_API_URL` and `ADMIN_API_KEY` in `.env.local`, then:

1. Give an account staff rights by setting `accounts.rights` to `MOD` or `ADMINISTRATOR`.
2. Sign in at `/admin/login`. The first sign-in shows a QR code to add to an authenticator app.
   This also turns on the authenticator for that account's in-game login.

Moderators can view players, kick, mute and ban (with a reason), see each player's punishment
history, leave staff notes, see characters that share an address, handle player reports, and
search by name or email. Administrators also get server controls, broadcasts, item spawning,
teleports, IP addresses and IP search, staff management (promote and demote moderators, reset a
lost authenticator) and the audit log. Every staff action is written to
`.data/web-panel-audit.log`.

Treat the admin API like the database: keep it on `127.0.0.1`, and if the site runs elsewhere,
reach it over a private network or tunnel rather than a public reverse proxy.

## Deploying

- **Same machine as the game server (simplest):** `npm run build && npm start` behind a reverse
  proxy (nginx, Caddy) that terminates HTTPS. Keep the API on `127.0.0.1`.
- **Vercel or another host:** the API must be reachable from that host. Put it behind a reverse
  proxy with HTTPS (for example `https://api.example.com` forwarding to `127.0.0.1:8091`), set
  `GAME_API_URL` to that address and `GAME_API_KEY` to the key. Never expose port 8091 directly
  over plain HTTP: the key and passwords would travel unencrypted.
- Enable Turnstile (`NEXT_PUBLIC_TURNSTILE_SITE_KEY` + `TURNSTILE_SECRET_KEY`) on any public
  deployment to keep bots from mass-registering names.

The client IP sent to the API for rate limiting is taken from `cf-connecting-ip`, `x-real-ip` or
the first `x-forwarded-for` entry, so make sure your proxy sets one of them.

## Content

- **News:** add a markdown file to `content/news/` with `title`, `date` (YYYY-MM-DD) and `summary`
  frontmatter. Newest date first.
- **Roadmap:** add a markdown file to `content/roadmap/` with `title`, `tagline`, `status`
  (`completed` / `in-progress` / `planned` / `ongoing`), `when` and `order`. Checklist lines
  (`- [x] …` / `- [ ] …`) become the milestone's detail dialog. `/roadmap` shows a Kryptic-style
  journey map (filters, dashed path, zigzag cards); click Explore for the checklist.
- **Hero image:** drop a game screenshot at `public/hero.jpg` and it appears behind the home page
  hero. Without it, the hero uses a plain gradient.
