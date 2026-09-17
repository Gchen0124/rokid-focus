# Live listen / convo HUD — agent handoff

**Read this before touching ASR, reactions, or the glasses convo UI.**

Project: `/Users/chenniuniu/Downloads/RokidFocus`  
Phone APK: `app/build/outputs/apk/debug/app-debug.apk` (`com.chenniuniu.rokidfocus`)  
Glass APK: `glass/build/outputs/apk/debug/glass-debug.apk` (`com.chenniuniu.rokidfocus.glass`)  
Glasses adb serial (when USB): `1906092611101786`

Keys live in **`local.properties`** (gitignored). Never commit secrets. Never put keys in Kotlin source.

---

## What the user is building

AR glasses **conversation helper**:

1. Glasses mic → phone over CXR pairing (not glasses Wi‑Fi).
2. **iFlytek realtime LLM ASR** transcribes (中英+方言). Roles: YOU / THEY / S2 / S3.
3. HUD shows a **growing dialogue** (newest at the bottom). Swipe temple to look at older lines.
4. **DeepSeek** writes 2 spoken replies + skip (`◆ REACT` A/B/C). Independent of ASR. Must stay pinned at the bottom.
5. Phone **Convo** tab is the persistent history (who + text + options).

Slogan / talk style: `怪奇实验室 + 外交官`.

---

## Do / don’t

- **Do not** use Doubao for live listen. Doubao ASR was replaced. `DoubaoAsr.kt` was deleted. Doubao key may still sit in `local.properties` for other work.
- **Do not** stop the ASR websocket on breaths, commas, or 800ms pauses. Long talk is **one paragraph per speaker** until the speaker changes or listen is toggled off.
- **Do not** send `react=["…"]` / ROLLING in a way that **replaces** existing A/B/C. Keep last options until new ones arrive.
- **Do not** mix reaction Caps into the ASR Caps. Channels are separate: `asr` vs `react`.
- **Do not** steal camera button (click / double-click / long-press). Temple: tap = listen, double-tap = exit, swipe = history while listening.
- **Do not** put the glasses project in iCloud `Documents/` — Gradle times out. Stay in `~/Downloads/RokidFocus`.

---

## Architecture

```
Glasses :glass                         Phone :app
──────────────                         ──────────
ConvoListen AudioRecord 16k mono
    │ PCM Caps "pcm" (rk_custom_key)
    ▼
                              CxrHudController.feedPcm
                              PhoneListen.onPcm
                              XfyunAsr  (role_type=2)
                                    │
                                    ├─ asr Caps → HUD transcript (never blocked)
                                    └─ react Caps → DeepSeek options (async)

Phone Listen button → Caps listen_start → glasses start recording
Glasses temple tap  → Caps listen_on    → phone start ASR
```

CXR-L session: **CUSTOMAPP** `com.chenniuniu.rokidfocus.glass`  
Caps: phone→glass `rk_custom_client`, glass→phone `rk_custom_key`.

Hi Rokid pairing ≠ Focus listen. **Focus phone app must be open and Connect glasses.**

---

## ASR (iFlytek)

Docs:

- Transcribe: https://www.xfyun.cn/doc/spark/asr_llm/rtasr_llm.html  
- Voiceprint: https://www.xfyun.cn/doc/spark/asr_llm/voice_print.html  
- WS: `wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1?...`  
- Auth: HMAC-SHA1 over sorted URL-encoded query (`XfyunSign.kt`).  
  `accessKeyId` = APIKey, secret = APISecret, `appId`, `utc` `yyyy-MM-dd'T'HH:mm:ss+0800`.

Handshake was verified from this Mac. **autominor (37 langs incl. Korean) returns 35020** on this APPID — not entitled. App tries `autominor` first, then falls back to **`autodialect` = 中文 + 英语 + 202 方言**. Korean needs a console ticket.

Send **1280 bytes / 40ms** PCM s16le 16kHz mono (`XfyunAsr` pump). Fill silence frames so the 15s idle timeout does not kill the socket. **Never close WS until listen off.**

Results: `msg_type=result`, `data.cn.st`. `type "1"` = partial, `"0"` = definite (model sentence end). `cw[].rl` is a **string**: `"0"` = same speaker, `"1"|"2"|…` = speaker id.

**Do not** treat missing `normal` as failure (that bug ate all transcripts).

Roles:

- `rl` + volume (loud ≈ wearer) + optional voiceprint `feature_ids` + `eng_spk_match=1`.
- Labels: `you` / `them` / `them2` / `them3`. HUD: YOU / THEY / S2 / S3.
- History stores `who` on every turn (`FocusStore` JSON, Convo tab).

Voice enroll: Desk **Register my voice (12s)** while listen is on, speak **alone** into the glasses. API `POST .../res/feature/v1/register`. Signature in **header only**, not query. Success `data` may be a JSON **string**. Surface real errors on Desk (`enrollLine`).

---

## Transcript model (PhoneListen)

- One **paragraph per speaker**. Breaths/pauses **append**, they do not start a new card and they do not stop listening.
- New paragraph only on **speaker change** or listen off.
- Live HUD text = paragraph + current partial. Phone Convo tab persists merged turns (up to 2000 chars each).

---

## DeepSeek reactions — intended policy

Live ASR (iFlytek) already emits **sentence ends** as `type=0` / `definite=true`. That is the right trigger, not breath detection.

**Use this:**

1. ASR keeps running no matter what DeepSeek is doing.
2. When a **non-YOU** utterance is `definite`, debounce ~280ms, then call DeepSeek with the **full 【convo】** plus the latest paragraph.
3. Output JSON `{"replies":["…","…","skip"]}` — two spoken options + skip. Style from Desk **Talk style**.
4. Send on Caps `react` only. **Do not** rewrite `asr` text.
5. Keep previous A/B/C on screen until the new payload arrives. No full-screen ROLLING.
6. If the user never picks an option, still **replace** options as the talk moves on.

Do **not** fire on every partial (too many calls, flicker).  
100-char batching is a fallback only; sentence-level from the live model is preferred.

Model: `deepseek-flash`, `thinking: disabled`, `response_format: json_object`.  
Key: `local.properties` `deepseek.api.key` and/or Desk field.

---

## Glasses HUD layout (must keep)

While `convoActive`:

```
[clock | rings]          ← compact
[transcript viewport]    ← weight 1, newest lines pinned to BOTTOM
[◆ REACT A/B/C]          ← fixed footer, never squeezed off
[mic line + hints]
```

Long text: wrap ~20 glyphs/line, show last ~7 lines. `convoScroll` looks backward.  
Temple **swipe** while listening = history (`swipe=history`). New ASR resets scroll to live.

Selecting A/B/C as an action is **not wired** yet (UI only).

---

## Caps protocol (listen)

| dir | name | fields |
|-----|------|--------|
| G→P | `pcm` | binary PCM |
| G→P | `listen_on` / `listen_off` | temple |
| P→G | `listen_start` / `listen_stop` | phone Listen button |
| P→G | `asr` | text, flag, who |
| P→G | `react` | draft0, draft1, skip |
| P→G | `convo_hist` | previous paragraphs |
| P→G | `listen_state` | live/off/err, msg |

Phone Listen **must** send `listen_start` or glasses will not record (that bug: App Listen did nothing).

---

## Key files

| File | Role |
|------|------|
| `app/.../listen/XfyunAsr.kt` | iFlytek WS + 40ms pump |
| `app/.../listen/XfyunSign.kt` | HMAC-SHA1 |
| `app/.../listen/XfyunVoicePrint.kt` | enroll |
| `app/.../listen/PhoneListen.kt` | PCM, paragraphs, roles, DeepSeek trigger |
| `app/.../listen/Drafts.kt` | DeepSeek JSON replies |
| `app/.../listen/ConvoMemory.kt` | in-memory + JSON turns |
| `app/.../glasses/CxrHudController.kt` | CXR CUSTOMAPP, Caps, listen_start |
| `app/.../ui/FocusScreen.kt` | Desk / Convo / Listen / enroll |
| `app/.../data/FocusStore.kt` | persisted convo + talk style + voice id |
| `glass/.../ConvoListen.kt` | glasses mic → PCM Caps (send **all** samples while on) |
| `glass/.../cxr/FocusBridge.kt` | Caps in/out |
| `glass/.../ui/GlassHud.kt` | transcript viewport + ReactMenu |
| `glass/.../KeyReceiver.kt` | temple gestures |
| `glass/.../MainActivity.kt` | listen from phone + temple |

---

## Build / install

```bash
cd /Users/chenniuniu/Downloads/RokidFocus
./gradlew :app:assembleDebug :glass:assembleDebug
adb -s 1906092611101786 install -r glass/build/outputs/apk/debug/glass-debug.apk
adb -s 1906092611101786 shell am start -n com.chenniuniu.rokidfocus.glass/.MainActivity
# Phone: sideload app/build/outputs/apk/debug/app-debug.apk
```

Test: Focus on phone → Connect glasses (mic auth) → **Listen on Desk** → talk. HUD `mic xfyun`. Long sentence with breaths should **keep growing**. React bar stays at bottom. Swipe temple to see older lines.

---

## Known gaps / next

- Korean: enable **autominor** on this iFlytek APPID (工单). Until then only 中英+方言.
- Voiceprint `data: {"status":2}` = audio rejected (too quiet / not speech). Need 12s **solo** speech.
- Picking a reaction (speak it / dismiss) has UI, no action yet.
- Caps size warning on long `asr` / `convo_hist` — keep payloads bounded (`takeLast(500)` on live line).
- CXR `sendCustomCmd` is deprecated; still what CUSTOMAPP uses.
- Do not call `startAudioStream` on CXR-L while CustomApp records — it steals the glasses mic and the second listen dies.

---

## local.properties keys (names only)

```
xfyun.app.id=
xfyun.api.key=
xfyun.api.secret=
deepseek.api.key=
doubao.api.key=          # unused for listen; keep for other features
```
