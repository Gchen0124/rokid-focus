# Opportunity hunting

Standing loop. Any agent (Grok, Claude, Codex) can take this cold.

Source of truth for **dates** is the Notion DB **funding sources**.
Source of truth for **today’s conclusions** is `memory/hunting/reports/YYYY-MM-DD.md`.
The HUD task `opps hunting dl` stays **human-owned for applying**. Agents hunt, write, and set `ready`. Never mark Done.

## Start here (other agents, every day)

1. Read this file, then `PLAYBOOK.md`.
2. Open or reuse `memory/queue/1786973510012-opps-hunting.md`.
3. Flip `mock/tasks.json` → that id → `assignee: agent`, `agentStatus: doing`.
4. Run the daily timeline in `PLAYBOOK.md`.
5. Write `memory/hunting/reports/YYYY-MM-DD.md` from `templates/daily-report.md`.
6. Update Notion dates (`Apply open` / `Apply close` / `Next check`). Keep `reschedule at？` as the same YYYY-MM-DD for older views.
7. End `agentStatus: ready`. Do **not** set `done: true`.
8. One line in `memory/days/YYYY-MM-DD.md`.

If yesterday’s report already exists and `Next check` rows are current, still run the daily pass — the job is *time change + new programs*, not a one-shot research dump.

Forward look (Sep 2026–Aug 2027) plus “start 6–8 weeks early next year” alarms: `CALENDAR-2026-09-to-2027-08.md`.

## Map

| File | Role |
|---|---|
| `PLAYBOOK.md` | Daily / weekly / biweekly timeline |
| `SCHEMA.md` | Notion ids, columns, hygiene rules |
| `CHANNELS.md` | X / newsletter / YouTube per org |
| `templates/daily-report.md` | Report shape |
| `reports/` | Dated daily reports |
| `CALENDAR-2026-09-to-2027-08.md` | Forward calendar + missed-cycle alarms (doc first; not Notion yet) |

## Notion

- Database: [funding sources](https://app.notion.com/p/1e9d6707fb138076a067de0ae4e86b15)
- Data source: `collection://1e9d6707-fb13-8088-a7a7-000b72211950`
- Views to use: **Apply windows** (table, sort by Apply close), **Next check calendar**

## Hard rules

- Verify dates on the **official apply page**, not a listicle.
- Write the date into Notion the same day you verify it.
- Do not invent Apply close. If rolling / unknown, leave Apply close empty and set Next check + Cadence.
- New org rows are allowed. Duplicate rows are not — merge into the existing Goal name.
- Human apply / money / email-as-user is out of scope.
