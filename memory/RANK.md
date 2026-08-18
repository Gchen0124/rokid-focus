# Rank formula

Glasses show **EV · time · title · ETF**, sized by EV. Slogan sits under the clock. Cutoff is **17:30**. Everything below is computed or talked — not drawn as extra chrome.

## Fields (laptop / json — add as we implement)

| Field | Meaning |
|---|---|
| `usd` | payoff if it works (upside) |
| `p` | 0–1 probability it actually pays (default 1 until we set it) |
| `ev` | `usd × p` — **this is what the HUD calls $** |
| `minutes` | estimated time |
| `urgency` | `now` / `today` / `week` / `later` (or a deadline that maps to those) |
| `energy` | task needs `deep` / `normal` / `low` |
| `blockedBy` | task ids that must go first |
| user energy | one cockpit toggle: Deep / Normal / Drained |

Until those fields exist in json, treat current `usd` as EV with `p = 1`, urgency = `today` unless the title says `dl` / `eod`, energy = `normal`.

## Gates then score

**Dependency is a gate.** If A blocks B, B cannot sit above A. Doing A first is the compounding:

```
unlockBonus(A) = 0.35 × sum(EV of tasks A unblocks)
```

**Urgency multiplier**

| Tag | Multiplier |
|---|---|
| now | 2.2 |
| today | 1.5 |
| week | 1.15 |
| later | 1.0 |

Title hints we already use: `dl` → at least `today`; `【eod】` → `today` + usually `low` energy.

**Energy fit**

| Task needs \ User is | Deep | Normal | Drained |
|---|---|---|---|
| deep | 1.0 | 0.55 | 0.25 |
| normal | 0.85 | 1.0 | 0.6 |
| low | 0.7 | 0.9 | 1.0 |

**Score** (not displayed)

```
if blocked: sink (visible on laptop, hidden on glasses)

score = (ev + unlockBonus) × urgency × energyFit / √hours
```

`√hours` keeps a sharp 15‑minute hit competitive with a long slog.

## Display

- Sort open (unblocked, not done, not archived) by `score`
- HUD: `ev  minutes  title  ETF` (no rank numbers). Bottom: `need · ETF (all open) · free/over 5:30`.
- Pin / spoken agreement beats the formula until the next rank pass
