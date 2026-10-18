# Capture

A small Android app for the Galaxy Tab S11: one tap captures what's on screen, a dictated
note goes with it, and the homelab concierge files it as a triaged Forgejo issue.

| Trigger | Does |
|---|---|
| **Idea** | Dictation only |
| **Report** | Screenshot of the app underneath (accessibility service), then dictation |
| **Record** | Silent screen clip, up to 30 s. Tap the tile or the notification to stop, then dictation |

Each is a quick-settings tile and an app shortcut (long-press the icon in the taskbar).

## Sending

1. `GET https://agents.example.invalid/api/health`. If it fails, a
   notification warns "Homelab not reachable. Not on home Wi-Fi and Tailscale off?" and the
   capture stays on the device until **Retry**.
2. `PUT` the screenshot or clip to
   `https://dufs.example.invalid/captures/<YYYYMMDD-HHMMSS>-<package>.<jpg|mp4>`.
3. `POST /api/sessions/concierge/input` with one line:

   ```
   Capture: {"kind":"idea|report|record","app":"<label>","package":"<pkg>","url":"<url>|null","file":"<dufs url>|null","said":"<dictated text>","at":"<ISO time>"}
   ```

   The format is shared with the orchestrator concierge (xiankai-github/concierge#18,
   xiankai-github/homelab-agent-orchestrator#310). Change it only together with those.
   `CapturePayloadTest` pins the exact line.

## Setup on the tablet

```sh
./gradlew installDebug
```

Open **Capture**, then:

- **Accessibility settings** → Installed apps → Capture → on. Report needs it; Idea and
  Record still work without it, with the app recorded as `unknown`.
- **Allow microphone and notifications.**
- **Add Idea / Report / Record tile.**
