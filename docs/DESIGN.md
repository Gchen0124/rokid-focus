# Rokid Focus

A 5-minute glance HUD for Rokid Glasses. The phone keeps your **priority** and **what you are doing** in front of your eyes, and plays a time-coded chime on every wall-clock 5-minute mark.

## Why this, not a kitchen-sink sample

The official CXR-L sample is a connection demo. This app is a focus loop:

- You set one priority and one current task on the phone.
- Glasses show those two lines plus the time.
- Every 5 minutes, aligned to the real clock (`:00`, `:05`, `:10`…), you hear a chime and the HUD flashes **CHECK IN**.
- Quarters and the hour use stronger, distinct sounds so you can tell time without looking at a watch.

Phone works even if glasses are offline. Glasses are an overlay, not a requirement.

## Chime map

| Minute | Kind | Sound | Meaning |
|---|---|---|---|
| `:00` | hour | 3 rising notes, longest | Whole hour |
| `:15` | quarter | 3 rising mid notes | 15 |
| `:30` | half | 2 lower notes | 30 |
| `:45` | three-quarter | 3 falling notes | 45 |
| `:05 :20 :35 :50` | five | two short high ticks | 5-family |
| `:10 :25 :40 :55` | ten | two short lower ticks | 10-family |

That is 6 files: hour + three quarter voices + two 5/10 ticks.

## Runtime

```
FocusService (foreground)
  └─ AlarmManager exact next :00/:05/:10…
        └─ ChimePlayer + FocusStore.checkIn
              └─ GlassesHud.update (if CXR session ready)
FocusActivity
  └─ edit priority / now doing
  └─ start/stop service
  └─ optional Connect glasses
```

## Glasses (optional)

CXR-L `CUSTOMVIEW` only. No glasses APK.

Session: companion app → token → `connect` → wait `onCXRLConnected && onGlassBtConnected` → `customViewOpen`.

HUD nodes: `timeView`, `priorityView`, `nowView`, `statusView`.

## Jumpable dependencies

See `README.md`. Core timer, persistence, UI, and chimes do not need glasses, JDK, or the Rokid Maven repo to be *written*. Opening the project in Android Studio and pairing glasses is for when you are back.
