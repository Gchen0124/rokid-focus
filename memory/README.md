# Memory flywheel

Goal: every priority talk makes the *next* agent cheaper and sharper — Grok today, Claude or Codex tomorrow.

```
talk (chat)
  → day note     (raw, dated, short)
  → standing md  (only if it will still matter in 2 weeks)
  → tasks.json   (what the eyes see)
        ↓ USB if glasses connected
      glasses HUD
```

## Three layers (do not collapse them)

| Layer | Files | Grows? | Rule |
|---|---|---|---|
| **Raw** | `days/YYYY-MM-DD.md` | one file per day | Append. Never rewrite history. Max ~40 lines. |
| **Standing** | `PROFILE.md` `PROJECTS.md` `PATTERNS.md` | stays small | Rewrite in place. Delete stale bullets. Date the change. |
| **Logic** | `RANK.md` `WORKFLOW.md` `DELEGATION.md` | rare | Change only when we agree the system changed. |
| **Queue** | `queue/<id>-<slug>.md` | one file per handoff | Delete when the human accepts. |

If a day note is repeating the same sentence three days in a row, **promote** it to PATTERNS or PROFILE and stop repeating it in days.

If a standing bullet has not been used in ~30 days and is not a Never, **delete or archive** it. Memory that cannot be forgotten cannot compound.

## What belongs where

- “I am drained after 9pm” → PROFILE (energy)
- “opps hunting is the $300k daily” → PROJECTS
- “Do not put deep work first when drained” → PATTERNS
- “This morning we put talent after 日报 because …” → that day’s file only
- Current list / done / minutes → `mock/tasks.json` only

## Agent write checklist (end of a rank pass)

- [ ] Day file exists and has energy + top 3 why + hits
- [ ] ≤2 standing-file edits, each one bullet, with `updated: YYYY-MM-DD`
- [ ] `tasks.json` matches what we said out loud
- [ ] No full chat paste

## Related systems (do not fork)

- Daily review / Never / Carry On: `~/.claude/skills/daily-review/SKILL.md` + Notion
- If a review produces a priority pattern, add one line to `PATTERNS.md` and point at the date
