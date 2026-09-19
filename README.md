# Rokid Focus

An **AR conversation assistant** for [Rokid Glasses](https://rokid.com) — live transcription, translation, and spoken reply suggestions on the waveguide — plus a small daily-priority HUD for when you are not in a conversation.

Two Android apps:

- **Phone** (`app/`) — the brain. It pairs with the glasses (CXR-L), runs ASR + translation + reply generation, and is the cockpit.
- **Glasses** (`glass/`) — the display + mic. A custom app on the waveguide HUD. Glanceable, all-green, no keyboard.

> New here? Read this file, then [`docs/LISTEN.md`](docs/LISTEN.md). `LISTEN.md` is the source of truth for the conversation pipeline and its hard rules. Extension ideas are in [`docs/RESEARCH-extensions.md`](docs/RESEARCH-extensions.md).

## The conversation assistant (the main bet)

You wear the glasses; you talk to someone. The glasses show what is being said, translated, and what you could say back.

```
glasses mic  ─┐
              ├─►  iFlytek realtime ASR  ──►  transcript (word by word)
phone mic    ─┘          (2 sessions)   ──►  sentence end
                                                    │
                                                    ▼
                                         DeepSeek: translate + 2 spoken
                                         reply options (A / B) + skip
                                                    │
                                                    ▼
                          glasses HUD: YOU left · THEY right · translation under
```

**Design decisions that matter:**

- **Two mics, two labels.** The glasses mic is the wearer (`YOU`); the phone mic is the other person (`THEY`, with blind speaker separation so several people can be told apart). Labels come from the *mic source*, not a volume guess.
- **Word-by-word.** iFlytek interim results (`type=1`) go on screen immediately; only on a sentence end (`type=0`) do we commit the turn and call DeepSeek. The phone mic sees several people, so its lane runs blind role separation; the glasses lane does not.
- **Translation is a second line.** The original streams live; the native-language translation appears under it after the sentence ends.
- **Cross-mic echo is dropped.** If your voice leaks into the phone mic, the duplicate `THEY` turn is removed — the glasses version wins.
- **The waveguide is a glance, not a log.** Only the current sentence is shown. Full history lives in the phone's **Convo** tab.

See [`docs/LISTEN.md`](docs/LISTEN.md) for the ASR/translation/reaction rules and the Caps protocol. Do not regress them.

## Repo map

| Path | Role |
|---|---|
| `app/` | Phone app: Desk / Convo / Listen UI, CXR link, iFlytek ASR, DeepSeek |
| `glass/` | Glasses app: HUD, mic recording, temple gestures, chimes |
| `docs/LISTEN.md` | **Conversation pipeline source of truth** |
| `docs/AGENT.md` | Design: voice-invoked personal agent (Hermes), Agent tab, image handling |
| `docs/RESEARCH-extensions.md` | Research: select-to-speak, agent (Hermes) integration, inline term lookup |
| `docs/DESIGN.md`, `docs/GLASS.md` | Earlier Focus HUD notes |
| `mock/` | Mac cockpit (HTML + `server.py`) for the priority list |
| `memory/` | Agent memory: rank, workflow, profile, projects, day notes |
| `app/.../listen/` | `PhoneListen`, `XfyunAsr`, `PhoneMic`, translations, dedupe |
| `glass/.../glass/` | `ConvoListen`, `FocusBridge` (Caps), `ui/GlassHud.kt` |

## Build and install

Needs JDK 17+, Android SDK, and the glasses on USB with `adb`.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

./gradlew :app:assembleDebug :glass:assembleDebug --no-daemon

# glasses (find the serial with `adb devices -l`)
adb -s <glasses-serial> install -r -t -g glass/build/outputs/apk/debug/glass-debug.apk
adb -s <glasses-serial> shell am start -n com.chenniuniu.rokidfocus.glass/.MainActivity

# phone (sideload the other APK)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Test flow: open Focus on the phone → **Connect glasses** → **Listen** (grant mic) → talk. The phone mic should be held toward the other person.

## Keys

Keys live in **`local.properties`** (gitignored). Never commit them, never put them in Kotlin source.

```
xfyun.app.id=
xfyun.api.key=
xfyun.api.secret=
deepseek.api.key=
```

## The daily-priority HUD (kept, not the focus)

The repo started as a 5-minute glance HUD: a ranked work list on the Mac, mirrored to the glasses, with a chime every wall-clock `:00/:05/:10…` and a `17:30` cutoff. It still works.

- Laptop cockpit: `python3 mock/server.py` → http://127.0.0.1:8787/
- Source of truth: `mock/tasks.json`, USB-copied to the glasses on plug-in.
- Rank/EV details: `memory/RANK.md`. Agent contract: `AGENTS.md`.

## Working with agents

Any agent joining this repo should read [`AGENTS.md`](AGENTS.md) first, then `docs/LISTEN.md` if the task touches conversation. Reasoning stays in chat; decisions get written into the files.

## License

MIT. See [`LICENSE`](LICENSE).
