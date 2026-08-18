# Rokid Focus

A **5-minute glance HUD** for [Rokid Glasses](https://rokid.com) plus a **laptop command center**. You keep a ranked work list on the Mac. The glasses show only what you need in a look: expected value, time, name, and when that row finishes if you work the list in order.

Any agent continuing this project: **read [`AGENTS.md`](AGENTS.md) first.** Daily priority memory lives in [`memory/`](memory/).

## What you see

```
Mon, August 17th, W34, Q3, 2026
8:52 AM   2:15                 [time rings]
怪奇实验室
$300k  3h45  opps hunting dl      12:37 PM
$20k   1h    talent               1:37 PM
…
need 6h45  ·  ETF 3:37 PM  ·  over 5:30 by 2h
```

- **Laptop** (`mock/`): edit, Done / Restore / Archive. Color demo of the 480×640 HUD.
- **Glasses** (`glass/`): same layout, all-green waveguide. Chime on every wall-clock `:00/:05/:10…`. Click = dim, back = exit.
- **Source of truth:** `mock/tasks.json`. USB copy to the glasses when they are plugged into this Mac.

## Repo map

| Path | Role |
|---|---|
| `AGENTS.md` | Contract for Grok / Claude / Codex |
| `memory/` | Rank formula, daily workflow, profile, projects, day notes |
| `mock/index.html` | Laptop HUD + command center |
| `mock/server.py` | `GET/PUT /tasks` on `:8787` |
| `mock/push-when-rokid-connects.sh` | USB watcher → `adb push` |
| `glass/` | Glasses Android app (`com.chenniuniu.rokidfocus.glass`) |
| `app/` | Older phone CustomView experiment (not the current cockpit) |

## Run the laptop cockpit

```bash
python3 mock/server.py
# http://127.0.0.1:8787/
```

## Build and install the glasses app

Needs JDK 17+, Android SDK, glasses on USB with adb.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
./gradlew :glass:assembleDebug --no-daemon
adb install -r -t -g glass/build/outputs/apk/debug/glass-debug.apk
adb shell am start -n com.chenniuniu.rokidfocus.glass/.MainActivity
```

Push the current list (also what the watcher does):

```bash
adb push mock/tasks.json /data/local/tmp/focus_tasks.json
adb shell run-as com.chenniuniu.rokidfocus.glass cp /data/local/tmp/focus_tasks.json files/tasks.json
adb shell am start -n com.chenniuniu.rokidfocus.glass/.MainActivity
```

Keep `mock/push-when-rokid-connects.sh` running so every saved list hits the glasses within ~2s when USB is in.

## Task schema

```json
{
  "id": "…",
  "title": "opps hunting dl",
  "usd": 300000,
  "minutes": 225,
  "done": false,
  "doneAt": "",
  "archived": false
}
```

HUD **ETF** on a row = now + sum(minutes of this row and every open row above it).  
Bottom **ETF** = now + sum(all open minutes).  
Free / over is vs **17:30** local, not bedtime.

Rank talks (urgency, probability, blockers, energy) are in `memory/RANK.md`. Those fields are not all in JSON yet; today’s `usd` is treated as EV.

## Delegation (human ↔ agent)

Optional fields on a task: `assignee` (`me` | `agent`), `handoff`, `agentStatus` (`idle` | `queued` | `doing` | `blocked` | `ready`).

- Laptop **Give to agent** moves a row into the Agent section.
- Glasses hide `assignee: agent` so the glance stays human work.
- Protocol: [`memory/DELEGATION.md`](memory/DELEGATION.md). Other users keep the same files and swap `PROFILE.md`.

## License

MIT. See `LICENSE`.
