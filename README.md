# Capture

A small Android app for tablets (built on a Galaxy Tab S11): one tap captures what's on
screen, a dictated note goes with it, and an agent orchestrator's concierge files it as a
triaged issue.

A floating button sits over every app. Drag it anywhere; it remembers where.

| Trigger | Does |
|---|---|
| **Tap** → Report | Screenshot of the app underneath (the button hides for it), then dictation |
| **Long-press** → Idea | Dictation only |
| **Long-press** → Record | Screen clip with your voice from the mic, up to 30 s. The button turns blue; tap it or the notification to stop, then dictation |
| **Long-press** → Settings | Opens the settings page |
| **Long-press** → Hide | Removes the button until you tap the launcher icon again |

The button is drawn by the accessibility service, so it needs no overlay permission and
disappears when the service is off.

Tapping the **Capture** launcher icon only brings the floating button back; no screen opens.
Long-press the launcher icon → **Settings** for the settings page. If the button can't be
shown (the accessibility service is off), the launcher icon opens the settings page instead.

Dictation uses Google's speech recognition in Singapore English (`en-SG`), falling back to
the device language. It keeps listening through pauses until you tap **Stop**, appending
each phrase to an editable field. Tap into the field to fix it by hand, **Talk** to carry on.
Nothing is sent until you tap **Send**.

## Sending

Capture sends to an agent orchestrator that runs a `concierge` session. Set its address on
the settings page under **Orchestrator URL**. A build made with `CAPTURE_ORCHESTRATOR` set
in the environment starts with that address filled in.

1. `GET <orchestrator>/api/health`. If it fails, or no address is set, a notification says
   so and the capture stays on the device until it can be sent.
2. `POST <orchestrator>/api/sessions/concierge/input` with the line and, for Report and
   Record, the file:

   ```
   {"text": "Capture: {\"kind\":\"idea|report|record\",\"app\":\"<label>\",\"package\":\"<pkg>\",\"url\":\"<url>|null\",\"said\":\"<dictated text>\",\"at\":\"<ISO time>\"}",
    "file": {"name": "<YYYYMMDD-HHMMSS>-<package>.<jpg|mp4>", "data": "<base64>"}}
   ```

   The orchestrator's concierge parses this format, so change it only together with the
   concierge. `CapturePayloadTest` pins the exact line and body. The orchestrator caps files
   at 50 MB, which is why clips are capped at 8 Mbps.

## Building

`./gradlew assembleDebug` builds a debug APK. The version code is the HEAD commit's
timestamp, so build from a git checkout.

A signed release build reads its key from the environment: `RELEASE_KEYSTORE_PATH`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`, then
`./gradlew assembleRelease`. A debug build is signed with another key, so it has to be
uninstalled before a release build will install, and the other way round.

## Setup on the tablet

Open **Capture**, then:

- **Accessibility settings** → Installed apps → Capture → on → **Allow**. The floating button
  appears.
- **Allow microphone and notifications.**
- **Orchestrator URL:** enter the orchestrator's address and tap **Save orchestrator URL**,
  unless the build came with one.

## When the floating button is missing

The settings page says which of these it is:

- **Accessibility service: off.** Turn it on as above. Force-stopping Capture (App info ›
  Force stop) also switches it off, so avoid that.
- **On, but not running.** Android suspends every accessibility service while a UiAutomation
  session runs, for example a UI test or an agent driving the tablet through
  `adb shell uiautomator`. The button comes back by itself once that session ends. To end it,
  find the session's `app_process` with `adb shell ps -A | grep app_process` and `kill` it.
  Nothing on the tablet needs changing.

## License

Capture is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3 of
the License, or (at your option) any later version. See [LICENSE](LICENSE).
