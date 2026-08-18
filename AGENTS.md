# Agent entry — Rokid Focus / daily priority

Any agent (Grok, Claude, Codex) that lands here is joining an **ongoing daily prioritization loop**, not a greenfield app.

Read in this order, then stop reading unless the task needs more:

1. This file
2. `memory/WORKFLOW.md` — when to talk, what to write
3. `memory/RANK.md` — score formula
4. `memory/PROFILE.md` + `memory/PROJECTS.md` + `memory/PATTERNS.md`
5. `memory/days/YYYY-MM-DD.md` for **today** (and yesterday if morning)
6. `mock/tasks.json` — live open / done / archive list

Do **not** scrape the chat history, Notion, or the whole repo unless the user asks. The flywheel is these files plus `tasks.json`.

## What this system is

| Layer | Path | Role |
|---|---|---|
| Live state | `mock/tasks.json` | What is open, done, archived. Source of truth for HUD. |
| Logic | `memory/RANK.md`, `memory/WORKFLOW.md` | How we rank and how a day runs |
| Standing memory | `memory/PROFILE.md`, `PROJECTS.md`, `PATTERNS.md` | Who, what bets, what repeats |
| Daily raw | `memory/days/YYYY-MM-DD.md` | Today’s talk, energy, hits — short |
| Glance UI | glasses `:glass` + `mock/index.html` | EV · time · title only |
| Cockpit | `http://127.0.0.1:8787/` | Edit, Done, energy later |

## Hard rules

- **Laptop file wins.** Glasses are a glance. Never invent a second ranking UI.
- **Talk here, write the file.** Reasoning stays in chat. Decisions go into `tasks.json` + a day note.
- **No LLM on every keystroke.** Rank only when the user asks (`rank today`, `reprioritize`, `Ask Grok`, morning / mid-day / EOD pass).
- **Data sync ≠ AI.** Saving `tasks.json` is free. USB push when glasses are plugged in is free. Tokens only on a rank pass.
- **Do not dump transcripts** into memory. Distill. Promote durable facts into standing files.
- **Do not fight a pin.** If the user pins #1 or we just agreed an order, that order wins until the next rank pass.
- Day cutoff on the HUD is **17:30**. Capacity is free time before 5:30pm. Each open row ends with a cumulative **ETF**; the bottom line is ETF for the whole open stack.

## Rank pass (when asked)

1. Read the files above.
2. State current energy if known (Deep / Normal / Drained). Ask if missing and it matters.
3. Propose order with one line of *why* for the top 3. Use `memory/RANK.md`.
4. Wait for agreement (or small edits).
5. Write:
   - `mock/tasks.json` if fields/order/done changed
   - append `memory/days/YYYY-MM-DD.md` (template in `memory/templates/day.md`)
   - promote any durable fact into PROFILE / PROJECTS / PATTERNS (one bullet, dated)
6. If glasses USB is connected, the watcher pushes the file. If not, it waits for plug-in.

## Other product facts (do not regress)

- Glasses package: `com.chenniuniu.rokidfocus.glass`
- Command center: `python3 mock/server.py` → `:8787`
- USB push: `mock/push-when-rokid-connects.sh` (hash of `tasks.json`, ~2s)
- HUD row: `#  $  time  title` — **no** `$/h` column
- Chimes every wall-clock 5 minutes on the glasses
- Click = dim, back = exit
- Related but separate: `~/.claude/skills/daily-review/SKILL.md` (Notion Never / Carry On). Promote priority *patterns* here; do not duplicate that ritual.

## Language

User writes mixed 中文 + English. Standing memory: English headings, keep the user’s own names (`bunny`, `BP`, `日报`) verbatim.
