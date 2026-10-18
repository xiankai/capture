# Capture

A small Android app for the Galaxy Tab S11: one tap captures what's on screen, a dictated
note goes with it, and the homelab concierge files it as a triaged Forgejo issue.

| Trigger | Does |
|---|---|
| **Idea** | Dictation only |
| **Report** | Screenshot of the app underneath (accessibility service), then dictation |
| **Record** | Silent screen clip, up to 30 s. Tap the tile or the notification to stop, then dictation |

Each is a quick-settings tile, an app shortcut (long-press the icon in the taskbar) and a
launcher entry of its own, because the One UI Edge panel only takes whole apps.

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

- **Accessibility settings** → Installed apps → Capture → on. Report needs it; Idea and
  Record still work without it, with the app recorded as `unknown`.
- **Allow microphone and notifications.**
- **Add Idea / Report / Record tile.**
