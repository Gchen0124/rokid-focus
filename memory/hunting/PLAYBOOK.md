# Hunting playbook — daily / weekly / biweekly

Timezone for “today”: **America/Los_Angeles** unless the user says they are elsewhere. Write dates as `YYYY-MM-DD`.

Target wall time: **25–40 minutes** on a weekday. Weekly deep dig adds ~30 minutes on Monday (or the first hunt of the week).

## Daily timeline (every run)

| Clock | Min | Lane | Do |
|---|---|---|---|
| T+0 | 2 | Intake | Read yesterday’s report + this playbook + `SCHEMA.md`. Confirm today’s date. |
| T+2 | 8 | 1 · Notion check | SQL the funding DB. Pull rows where `Next check` ≤ today **or** `reschedule at？` ≤ today **or** `Apply close` in the next 14 days **or** Priority=High and Cadence=daily. |
| T+10 | 8 | 1a/1b dates | Open the official URL for each due row. If start/end/refresh/uncertainty changed, write `Apply open`, `Apply close`, `Timeline`, `note`. Bump `Next check` (and the text twin `reschedule at？`). |
| T+18 | 8 | 2 · SuperGrok research | Run **today’s rotation** below. Add new rows only if they are not already in Notion. |
| T+26 | 6 | 3 · Channels | For 3 orgs on the due list (or the weekly slice): confirm X, newsletter, YouTube in Notion + `CHANNELS.md`. Note any new program announced on those channels. |
| T+32 | 5 | Report | Write `reports/YYYY-MM-DD.md`. List: due-today, date changes, new rows, human apply-now, next-14-days, blockers. Set queue `ready`. |

Stop at 40 minutes even if the 188-row DB is not fully cleaned. Hygiene is a weekly job.

## What “Notion check” means (1c)

Daily is **not** a full scrape of 188 rows.

Daily = rows whose **clock fired**:

- `Next check` ≤ today
- or `Apply close` in 0–14 days
- or High + Cadence `daily` (open apply windows)
- or Timeline/note says “refresh / unknown / rolling” **and** last official verify is >7 days old

If a row has no `Next check` and no `reschedule at？`, it is **undated**. Do **not** boil the ocean. Add 5 undated High/like rows to today’s due list and give them a Next check before you leave.

## Cadence (how often to re-open an org)

| Cadence | Use when | Default bump |
|---|---|---|
| `daily` | Open apply window, deadline ≤14 days, late window still live | +1 day, or the deadline itself |
| `weekly` | Rolling / unknown / live apply with no cutoff | +7 days |
| `biweekly` | Stable investor, no program cycle, channel watch | +14 days |
| `watch` | Closed this cycle; next window known or far | the next expected open, or +60 days |

## Research rotation (lane 2)

Do **one primary slice** per weekday so the week covers the brief. Always do a 2-minute extra-angle pass.

| Weekday | Primary slice | Queries (adapt year) |
|---|---|---|
| Mon | Female founder + incubator | `women founders accelerator 20XX deadline`, TiE Women, Google Women Founders Fund, Pear Female Founder Circles, MassChallenge, Springboard, Halogen, Female Founders Fund |
| Tue | Solo founder + incubator | EF, Antler, Founder Institute, Pioneer, HF0 (repeat only), SPC Fellowship |
| Wed | International founder + incubator | SkyDeck GFP, Unshackled, Everywhere, EF Bridge, Parallel18, 500 Global |
| Thu | Angels who write checks | Boost Founder Start, 2048, Everywhere $250K, Halogen, January Ventures, Female Founders Fund, Super Ventures |
| Fri | Hackathons + events | hardware / AR / wearable / journalism / wearable-sensor + AWE / Reality Hack / ROBOPALOOZA / InfraJam / Sensors Converge |
| Any | Extra (required) | Whatever the due list or product thesis implies and the brief omitted. Current standing extras: HAX/SOSV, Activate Fellowship, Foundry/Shenzhen build sprints, edtech Shark Tank/GESA, AR glasses funds |

Each search: official page first, then one secondary confirmation. Record `verified official YYYY-MM-DD` in Timeline.

## Weekly (Monday, or first hunt after a gap ≥6 days)

Add ~30 minutes **after** the daily pass:

1. Deep-dig **5 stored orgs** (prefer High + `like` + last verify >14 days). Look for a *new program*, not just the same apply URL.
2. Dedup: untitled rows, duplicate Greylock/AI2/Reach Capital clones, rag-only pages (`for application rag` stays, Kind=`rag`, Cadence=`watch`).
3. Fill missing `Kind` + `Cadence` on High rows.
4. Channel completeness: any incubator/vc/angel High row missing X **or** newsletter **or** YouTube — find or mark `none` in `CHANNELS.md`.
5. One new-org search outside the DB (same extras list).

## Biweekly (every other Monday)

1. Broader new-org hunt: 8–10 queries, not just the rotation.
2. Re-read `CHANNELS.md` vs live profiles (handle changes, dead newsletters).
3. Propose (do not silently drop) rows that are `not ideal` + Done + no next cycle.

## Human vs agent

| Agent writes | Human only |
|---|---|
| Notion dates, Kind, Cadence, channels | Submit applications |
| Daily report + queue ready | Email / call as the founder |
| New watch rows | Mark HUD Done |

## SQL snippets

Data source: `collection://1e9d6707-fb13-8088-a7a7-000b72211950`

```sql
-- due today
SELECT url, "Goal name", Priority, Status, Timeline, "reschedule at？",
       "date:Apply open:start" AS apply_open,
       "date:Apply close:start" AS apply_close,
       "date:Next check:start" AS next_check,
       Kind, Cadence, "userDefined:URL" AS link
FROM "collection://1e9d6707-fb13-8088-a7a7-000b72211950"
WHERE date("date:Next check:start") <= date('now')
   OR date("reschedule at？") <= date('now')
   OR (date("date:Apply close:start") BETWEEN date('now') AND date('now','+14 day'))
ORDER BY "date:Apply close:start", Priority;
```

```sql
-- undated High
SELECT url, "Goal name", Timeline, "userDefined:URL"
FROM "collection://1e9d6707-fb13-8088-a7a7-000b72211950"
WHERE Priority = 'High'
  AND "date:Next check:start" IS NULL
  AND ("reschedule at？" IS NULL OR "reschedule at？" = '')
LIMIT 10;
```
