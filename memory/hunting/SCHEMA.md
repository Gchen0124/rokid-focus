# Notion — funding sources schema

## IDs

| Thing | Value |
|---|---|
| Database | `1e9d6707-fb13-8076-a067-de0ae4e86b15` |
| Data source | `collection://1e9d6707-fb13-8088-a7a7-000b72211950` |
| URL | https://app.notion.com/p/1e9d6707fb138076a067de0ae4e86b15 |
| Apply windows view | `view://3c0d6707-fb13-8197-bb4f-000c8fbf4fe7` |
| Next check calendar | `view://3c0d6707-fb13-81f8-8646-000c6718e96c` |

~188 rows (2026-08-18). Most `Notion Calendar Period` cells are empty — do not rely on that column.

## Columns that matter for hunting

| Column | Type | Agent writes? | Meaning |
|---|---|---|---|
| Goal name | title | yes, if creating | Org + program, not a blog title |
| URL | url | yes | Official apply or org page |
| Status | status | yes | `In progress` = live hunt. `Done` = this cycle closed / not applying |
| Priority | select | yes | High = fit + action possible |
| Timeline | text | yes | Human-readable window + last `Verified official YYYY-MM-DD` |
| note | text | yes | Fit, constraints, next action |
| reschedule at？ | text | yes | **YYYY-MM-DD only.** Twin of Next check for old views |
| Notion Calendar Period | date | optional | Program *run* dates (start–end of the cohort), not apply dates |
| **Apply open** | date | yes | Application window start |
| **Apply close** | date | yes | Application deadline. Empty if rolling/unknown |
| **Next check** | date | yes | When an agent must reopen this row |
| **Kind** | select | yes | `incubator` `vc` `angel` `hackathon` `event` `program` `rag` `watch` |
| **Cadence** | select | yes | `daily` `weekly` `biweekly` `watch` |
| **X** | url | yes | Profile URL |
| **Newsletter** | url | yes | Subscribe or archive URL |
| **YouTube** | url | yes | Channel URL |
| Multi-select | multi | careful | Existing tags: `for application rag` / `check from time to time` / `not ideal` / `spot` / `like` |
| chance | number | optional | Do not fake |

## Date hygiene (1a + 1b)

1. **Apply open / Apply close** = the application window. This is the “clearer list” the user asked for. Use the **Apply windows** view.
2. **Notion Calendar Period** = when the program *runs* (e.g. SkyDeck Nov 2 2026 – Apr 15 2027). Do not put the deadline here.
3. **Timeline** = uncertainty and refresh language (“rolling”, “late still accepted”, “next window Dec–Jan”, “site inconsistent”).
4. **Next check** + **reschedule at？** = the agent alarm. Same calendar day, two columns, until old views die.
5. If the official page contradicts last Timeline, overwrite Timeline and say what changed.

## Creating / updating rows

- Create under the data source, not a random page.
- Date properties use expanded keys: `date:Apply close:start` = `2026-08-21`.
- URL column in updates is `userDefined:URL`.
- Do not drop columns. Do not rename Multi-select tags.

## Hygiene backlog (do on weekly, a few at a time)

- Untitled High rows that are Betaworks clones (same Timeline, null Goal name) — merge or title them.
- Duplicate Greylock Edge / Reach Capital / AI2 apply vs org pages — keep one live row, Kind=`rag` on the other.
- `Increase sales by 20%` and other non-funding goals — Kind=`watch`, low priority, or leave untouched if unsure.
- Fill Apply open/close for any High row that already has a hard date in Timeline text.
