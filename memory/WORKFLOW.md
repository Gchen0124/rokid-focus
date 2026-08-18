# Daily prioritization workflow

Three passes, same files. Along the day, not only at 8am.

## Morning (open the day)

1. Read `PROFILE.md`, `PROJECTS.md`, `PATTERNS.md`, yesterday’s day file, `tasks.json`.
2. Ask energy if not stated: Deep / Normal / Drained.
3. Rank open tasks (`RANK.md`). Blocked work stays off the glasses.
4. Agree top 3. Write `memory/days/today.md` + update `tasks.json`.
5. User works from glasses glance + laptop Done.

## Along the day (after a hit, a slip, or a new fire)

Trigger: user says they finished something, energy changed, or a new task appeared.

1. Read today’s day file + `tasks.json` (Done hits are already in the file).
2. Re-score only what moved. Do not reshuffle the whole list unless they ask.
3. Append 2–4 lines to the day file: time, what hit, what is now #1 and why.
4. If glasses are on USB, push is automatic.

## Evening (close vs 17:30)

1. Compare Done today vs what we named in the morning.
2. One paragraph in the day file: kept / slipped / why.
3. Promote at most one pattern or project fact.
4. Leave tomorrow a single “start here” line in the day file.
5. Optional: user runs `/daily-review` separately (Notion). Do not duplicate that dump here.

## “Ask Grok / reprioritize” button (product)

The laptop button is a **doorbell**, not an in-app model.

- It means: the file is ready, come read `tasks.json` + `memory/` in chat.
- Tokens = one pass. Not a daemon.
- After we agree, the Open list *is* the recommendation. No second AI panel.

## USB / sync (not AI)

| Event | Action |
|---|---|
| Any committed laptop change | write `tasks.json` |
| Glasses plugged in | `push-when-rokid-connects.sh` copies file + brings Focus up |
| Glasses in the case | file waits; next plug-in pushes |
| Agent rewrote `tasks.json` | same as a laptop change |

Do not add Wi-Fi sync unless USB-on-change starts failing real days.
