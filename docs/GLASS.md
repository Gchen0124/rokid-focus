# Glasses CustomApp (`:glass`)

Runs **on Rokid Glasses**. Independent of the phone CustomView HUD you are testing now.

| | Phone (`:app`) | Glasses (`:glass`) |
|---|---|---|
| You open it | Phone | Glasses launcher |
| SDK | CXR-L CustomView | CXR-S `cxr-service-bridge` |
| Chimes | Phone speaker | Glasses speakers |
| Edit priority / now | Phone fields | Phone commands, or last saved values |

## Open on glasses

1. Build `glass/build/outputs/apk/debug/app-debug.apk`
2. Install on glasses (`adb install` if the glasses are in debug, or phone CustomApp upload later)
3. Open **Focus** on the glasses
4. HUD stays up; every `:00/:05/:10…` you hear a chime and see CHECK IN
5. Click the glasses key / tap to clear CHECK IN

## CXR-S commands (phone → glasses)

Channel `rk_custom_client`:

- `set_priority`, `<text>`
- `set_now`, `<text>`
- `still_on_this`

Glasses → phone channel `rk_custom_key`: `check_in`, `<kind>`, `<HH:mm>`
