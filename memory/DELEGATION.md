# Delegation — human ↔ agent

Reusable protocol. This repo’s operator is one user; any fork keeps the same files and swaps `PROFILE.md`.

Two seats only:

| Seat | Does | Does not |
|---|---|---|
| **me** | Body, calls, money, Done on the glasses glance | Wait on the agent to “feel” a day |
| **agent** | Research, drafts, file edits, rank passes, repo work | Mark Done, send money, speak as the user |

The glasses stay a **glance of human work**. Agent work is visible on the laptop cockpit and in `memory/queue/`, not as extra chrome on the waveguide.

## Schema (on each task in `mock/tasks.json`)

| Field | Values | Meaning |
|---|---|---|
| `assignee` | `me` (default) · `agent` | Who is supposed to move it |
| `handoff` | short text | What “done enough” looks like for the agent |
| `agentStatus` | `idle` · `queued` · `doing` · `blocked` · `ready` | Agent lane only. `ready` = agent finished, human reviews |

Human Done still uses `done` / `doneAt`. An agent **never** sets `done: true` unless the user says the output is accepted.

## How a handoff starts

User says *delegate this*, *you take X*, or taps **Give to agent** on the laptop.

Agent then:

1. Set `assignee: agent`, `agentStatus: queued`.
2. Create `memory/queue/<id>-<slug>.md` from `memory/templates/handoff.md`.
3. Reply with one line: what you will do, and when you will write `ready`.
4. Append two lines to today’s day file.

## How the agent works a queued item

1. Read `AGENTS.md` → this file → the queue note → `tasks.json`.
2. Flip `agentStatus` to `doing`.
3. Do the work. Write artifacts in the repo (not in chat-only).
4. End in one of:
   - `ready` — output path listed in the queue note; ask the user to accept
   - `blocked` — one sentence why, and what you need
5. Do **not** mark the HUD task Done.

## How the human closes it

- Accept → user hits **Done** (or says accept). Agent may then archive the queue note into the day file and delete the queue file.
- Reject / take back → `assignee: me`, `agentStatus: idle`.
- More work → stay `agent`, new handoff sentence.

## What belongs in `memory/queue/` vs chat

- Queue note = the contract (goal, constraints, output path).
- Chat = the messy middle.
- Day file = one line when it queued / ready / accepted.

Do not paste transcripts into the queue. Other users and other agents should be able to pick up a queue file cold.

## Triggers (any agent)

`delegate`, `you take`, `agent queue`, `what's on your plate`, `accept`, `take it back`.

When asked “what's on your plate”, list `assignee == agent` from `tasks.json` and the open files in `memory/queue/`.
