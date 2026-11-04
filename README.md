# RiderDeck

A riding dashboard for an Android phone mounted on a bike: GPS speed and trip, lean angle, YouTube Music controls, Google Maps turn-by-turn on the dashboard, big call buttons, WhatsApp read-aloud, voice commands, crash detection with SOS text, heat guard and a fuel range estimate.

## Get the app

Every push to `main` builds an APK with GitHub Actions and publishes it under **Releases**. On your phone, open the latest release, download `RiderDeck-<n>.apk` and install it (allow "Install unknown apps" for your browser when asked). New builds install over old ones.

## First run

1. Allow **Location** (speed, trip, map).
2. Allow **Notification access** (music controls, Maps turns, calls, messages).
3. Optional: **Phone** (answer/decline), **SMS** (SOS), **Microphone** (voice).
4. Add emergency contacts in More → Settings.

## Notes

- Keep RiderDeck open while riding. When you start a route, Google Maps opens; switch back to RiderDeck and the next turn shows on the dashboard.
- Tyre pressure needs Bluetooth TPMS valve-cap sensors; support for a specific kit comes in a later build.
- `keystore/debug.keystore` is a throwaway signing key so builds update in place. Don't reuse it for anything else.
