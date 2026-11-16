# Changelog

All notable changes to the server. Newest first.

## Unreleased

### Agility
- **Draynor Village Rooftop Course** (level 1). Movement, animations and
  timings match live OSRS, taken from a packet capture.
- **Gnome Stronghold Agility Course** (level 1): log balance, obstacle nets,
  tree branches, tightrope and pipes. 110.5 XP per lap including the 50 XP lap
  bonus. Gnome trainers shout encouragement as you go.
- **All rooftop courses** are now available, with current OSRS XP per
  obstacle:
  - Al Kharid (level 20, 216 XP per lap)
  - Varrock (level 30, 269.7 XP)
  - Canifis (level 40, 240 XP)
  - Falador (level 50, 586 XP)
  - Seers' Village (level 60, 570 XP)
  - Pollnivneach (level 70, 890 XP)
  - Rellekka (level 80, 780 XP)
  - Ardougne (level 90, 889 XP)
- **Failing rooftop obstacles**: the same obstacles that can fail in OSRS drop
  you to the ground for damage. The chance to fail shrinks as your Agility
  rises, until you stop failing at the OSRS level for that course.
- Every rooftop obstacle restores 1-2% run energy.
- **Marks of Grace** drop on every course, following the OSRS rules: a
  3-minute cooldown between marks and an 80% lower chance once your base
  Agility is 20+ levels above the course. Canifis and Ardougne have double the
  usual chance, Rellekka slightly higher, and Canifis never applies the
  over-level reduction. Ardougne marks always spawn on the same tile.
- **Lap counts** for each course, shown in chat after each lap.
- **Grace** in the Rogues' Den sells the Graceful outfit and amylase packs for
  Marks of Grace (full set: 260 marks). Items sell back for 80%.
- **Lap counter toggle**: ask Grace, or right-click her, to turn lap count
  messages on or off for every course.

### Website
- **Roadmap** at `/roadmap`: a timeline of milestones with overall progress, per-milestone
  checklists and Explore pages. Edited as markdown under `website/content/roadmap/`.
- **Player profiles** with skills, recent XP gains and a 30-day XP graph, and a
  page to **compare** two players side by side.
- **XP tracker**: the top gainers over the last day, week or month, overall or
  for each skill.
- **Item and monster database** with drop tables and drop rates taken straight
  from the game's drop tables.
- **Account page**: sign in with your game login to change your password, turn
  your authenticator on or off, see your characters and link Discord.
- **Report a player** from your account page, or in game with
  `::report name, what happened`.
- A **status page**, and a banner across the site during restarts and
  maintenance.
- This changelog is now on the website.

### Discord
- Level 99s and maxed totals are announced in Discord.
- News posts can be sent to a Discord channel.

### Staff admin area
- The website has a staff area at `/admin`: server status, online players,
  maintenance mode, XP rate, broadcasts, scheduled restarts and shutdowns, and
  bans.
- **Player lookup**: account details, position, skills, inventory, equipment
  and bank for any character, online or not.
- Staff sign in with their game account (`MOD` or `ADMINISTRATOR` rights) and
  an authenticator app. The first sign-in sets up the authenticator, which also
  protects that account's in-game login.
- Moderators can kick, ban and unban players of lower rank. Everything else is
  for administrators only.
- Sign-in attempts are rate-limited, codes can't be reused, and sessions end
  when a staff member loses their rights. Every staff action is written to an
  audit log with the staff member's name.
- **Mutes**: staff can mute players for a set time or permanently. Muted players
  can't use public or private chat.
- Bans and mutes need a reason, and each player has a **punishment history** and
  private **staff notes**.
- Staff can see other characters that have logged in from the same address,
  handle **player reports**, and search by name or email. Administrators can
  also search by IP address.
- Administrators can promote and demote moderators, reset a lost authenticator,
  and read the **audit log** on the site.
- The game server no longer serves its own panel page. It exposes a private
  admin API on `127.0.0.1:8090` that only the website uses.

### Known issues
- Rooftop courses after Draynor are not yet checked against live OSRS. Expect
  small differences in animations, timings, landing spots and Mark of Grace
  spawn tiles.
- Rooftop fall messages, fall landing spots and some fall damage are
  placeholders. Al Kharid and Varrock stop failing at an assumed level 40 and
  50, since the wiki doesn't give one.
- Achievement diary bonuses for rooftops (extra marks, extra XP, faster mark
  cooldowns) are not in yet.
- Canifis doesn't check for Priest in Peril yet.
- Grace's "View Laps" option does nothing yet.
- Gnome pipe: the player doesn't visually blend into the pipe, and two players
  can squeeze through the same pipe at once.
