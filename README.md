# Capture

A small Android app for the Galaxy Tab S11: one tap captures what's on screen, a dictated
note goes with it, and the homelab concierge files it as a triaged Forgejo issue.

A floating button sits over every app. Drag it anywhere; it remembers where.

| Trigger | Does |
|---|---|
| **Tap** → Report | Screenshot of the app underneath (the button hides for it), then dictation |
| **Long-press** → Idea | Dictation only |
| **Long-press** → Record | Screen clip with your voice from the mic, up to 30 s. The button turns blue; tap it or the notification to stop, then dictation |
| **Long-press** → Hide | Removes the button until you open the app again |

The button is drawn by the accessibility service, so it needs no overlay permission and
disappears when the service is off.

Dictation uses Google's speech recognition in Singapore English (`en-SG`), falling back to
the device language. It keeps listening through pauses until you tap **Stop**, appending
each phrase to an editable field. Tap into the field to fix it by hand, **Talk** to carry on.
Nothing is sent until you tap **Send**.

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

The tablet installs and updates Capture through Obtainium. Every merge to `main` builds a
signed release APK (`.forgejo/workflows/publish.yml`), uploads it over the last one at
`https://dufs.example.invalid/apps/capture.apk`, and records it as a Forgejo
release (the latest three are kept). Obtainium reads the dufs URL rather than the releases,
because this repo is private and Obtainium's Forgejo source has no token field.

In Obtainium: **Add app** → App source URL
`https://dufs.example.invalid/apps/capture.apk` (detected as a direct APK
link, same as Remote Explorer) → **Add** → **Install**. Tailscale must be on when Obtainium
checks.

The release key is in the repo's Actions secrets (`RELEASE_KEYSTORE_BASE64`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`). A debug build
from `adb install` is signed with another key, so it has to be uninstalled before the
release build will install, and the other way round.

Open **Capture**, then:

- **Accessibility settings** → Installed apps → Capture → on. The floating button appears.
- **Allow microphone and notifications.**
