# Hermes for Kompakt (sample)

A small MMD sample app that lets a **Mudita Kompakt** manage sessions on a
[Hermes Agent](https://hermes-agent.nousresearch.com/) (Nous Research) running in the cloud,
and receive agent messages in a way that fits MuditaOS K's notification model.

> Status: prototype. Written against the published Hermes docs; it has not yet been
> built or run on a Kompakt. See "Open questions" before relying on it.

## Why it is shaped this way

**What the Kompakt does with notifications.** MuditaOS K has no notification tray, by design.
Mudita staff on the forum: *"The only notifications you'll receive are for: Alarms, Calendar
events, Incoming calls"*, shown full screen. Sideloaded apps (WhatsApp, Signal) only get an
audible ping and nothing on screen. There are no Google Play Services, so there is no FCM push.

That gives three rules for this app:

1. **Normal agent messages make no noise and no notification.** They go to an in-app
   **Inbox** you read when you choose to open the app.
2. **Urgent messages use the alarm path.** A high-importance notification with a
   full-screen intent (category `alarm`) opens a static, black-on-white `UrgentAlertActivity`.
   This is the only route on Kompakt that reaches the screen, and it is reserved for messages
   the agent marks urgent (ntfy priority 5, or the `urgent` tag; the threshold is a setting).
3. **No FCM.** A foreground service keeps one HTTP stream open to an ntfy topic that
   Hermes publishes to. Its required ongoing notification is on a `MIN`-importance channel.

**E Ink.** Agent replies are rendered once, when the run completes, not token by token.
Only "Working: <tool>…" status lines update during a run. Lists refresh on a button, not on a
timer. All UI comes from MMD (`ThemeMMD`, `TopAppBarMMD`, `LazyColumnMMD`, `TextFieldMMD`,
`ButtonMMD`, `HorizontalDividerMMD`).

## Architecture

```
 Kompakt (this app)                          Cloud host
 ┌─────────────────────────┐   HTTPS + key   ┌──────────────────────────────┐
 │ Sessions / Chat screens │ ──────────────► │ Hermes API server :8642      │
 │   HermesClient          │ ◄── SSE ─────── │  /api/sessions, …/chat/stream│
 │                         │                 │                              │
 │ NtfyListenerService     │ ◄── JSON stream │ ntfy topic ◄── Hermes ntfy   │
 │   ├─ priority < 5 → Inbox                 │   adapter, cron `deliver`,   │
 │   └─ priority 5  → full-screen alert      │   `hermes send ntfy:…`       │
 └─────────────────────────┘                 └──────────────────────────────┘
```

* **Talking to the agent** uses the Hermes API server's session endpoints:
  `GET /api/sessions`, `POST /api/sessions`, `GET /api/sessions/{id}/messages`,
  `POST /api/sessions/{id}/chat/stream` (SSE: `assistant.delta`, `tool.*`, `run.completed`).
* **The agent reaching you** uses Hermes's ntfy platform. Cron jobs can `deliver="ntfy"`,
  scripts can run `hermes send ntfy:<topic> "…"`, and replies to ntfy-originated chats come
  back on the same topic.

## Hermes host setup

`~/.hermes/.env`:

```
API_SERVER_ENABLED=true
API_SERVER_KEY=<long random secret>
# API_SERVER_HOST defaults to 127.0.0.1. Keep it there and expose it via Tailscale
# (tailscale serve) or a TLS reverse proxy rather than binding 0.0.0.0.

NTFY_SERVER_URL=https://ntfy.example.com   # self-host, or a reserved topic on ntfy.sh
NTFY_TOKEN=tk_...
NTFY_TOPIC=hermes-<random>
NTFY_ALLOWED_USERS=hermes-<random>
NTFY_HOME_CHANNEL=hermes-<random>
```

Then `hermes gateway restart`. To tell the agent how to flag urgency, add a line to its
instructions, for example: *"Only use ntfy priority 5 for things that need me within the
hour; everything else is priority 3."*

## App setup

Install the APK (`./gradlew :samples:hermes-kompakt:assembleDebug`, then `adb install`),
open it, and fill in the Hermes URL, API key, ntfy server, topic and token. "Test" calls
`/health`. Saving starts the listener; it also restarts after a reboot.

## Open questions to check on a real device

1. **Does MuditaOS K honour a sideloaded app's full-screen intent?** This is the whole urgent
   tier. If not, fallbacks are: `SYSTEM_ALERT_WINDOW` and starting the alert activity directly,
   or writing an event into the Kompakt calendar a minute ahead so the system's own calendar
   alert fires.
2. **Battery cost of the always-open ntfy stream.** If it is too high, switch to a periodic
   poll (`GET /<topic>/json?poll=1&since=<id>`) via WorkManager every 15 minutes and accept
   that only the urgent tier needs instant delivery.
3. **Session JSON field names.** The Hermes docs list the session endpoints but not full
   response bodies, so `HermesClient` parses several likely field names. Adjust once you see
   a real response.
4. **Sessions owned by other surfaces.** Hermes returns `409 permission_denied` if the API
   tries to drive a session owned by the CLI, TUI or a messaging platform. Sessions started
   here are fine; others are read-only from this app for now.

## Next steps

* Tool-approval prompts (`POST /v1/runs/{id}/approval`) as large Yes/No buttons, so the agent
  can ask permission from the phone.
* "Stop" for a running turn (`POST /v1/runs/{id}/stop`).
* Quiet hours that downgrade urgent to Inbox overnight.
* Move secrets to the Android Keystore.
