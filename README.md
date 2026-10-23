# Capture

A small Android app for the Galaxy Tab S11: one tap captures what's on screen, a dictated
note goes with it, and the homelab concierge files it as a triaged Forgejo issue.

A floating button sits over every app. Drag it anywhere; it remembers where.

| Trigger | Does |
|---|---|
| **Tap** → Report | Screenshot of the app underneath (the button hides for it), then dictation |
| **Long-press** → Idea | Dictation only |
| **Long-press** → Record | Silent screen clip, up to 30 s. The button turns blue; tap it or the notification to stop, then dictation |
| **Long-press** → Hide | Removes the button until you open the app again |

The button is drawn by the accessibility service, so it needs no overlay permission and
disappears when the service is off.

Dictation uses Google's speech recognition in Singapore English (`en-SG`), falling back to
the device language.

## Sending

1. `GET https://agents.example.invalid/api/health`. If it fails, a
   notification warns "Homelab not reachable. Not on home Wi-Fi and Tailscale off?" and the
   capture stays on the device until **Retry**.
2. `POST /api/sessions/concierge/input` with the line and, for Report and Record, the file:

   ```
   {"text": "Capture: {\"kind\":\"idea|report|record\",\"app\":\"<label>\",\"package\":\"<pkg>\",\"url\":\"<url>|null\",\"said\":\"<dictated text>\",\"at\":\"<ISO time>\"}",
    "file": {"name": "<YYYYMMDD-HHMMSS>-<package>.<jpg|mp4>", "data": "<base64>"}}
   ```

   The format is shared with the orchestrator concierge (xiankai-github/concierge#18,
   xiankai-github/homelab-agent-orchestrator#310). Change it only together with those.
   `CapturePayloadTest` pins the exact line and body. The orchestrator caps files at 50 MB,
   which is why clips are capped at 8 Mbps.

## Setup on the tablet

Install on the tablet only, since `installDebug` installs on every connected device:

```sh
./gradlew assembleDebug
adb -s <tablet serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Open **Capture**, then:

- **Accessibility settings** → Installed apps → Capture → on. The floating button appears.
- **Allow microphone and notifications.**
