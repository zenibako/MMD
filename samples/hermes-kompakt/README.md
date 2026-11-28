# Hermes for Kompakt (sample)

A small MMD sample app for chatting with a
[Hermes Agent](https://hermes-agent.nousresearch.com/) (Nous Research) from a
**Mudita Kompakt**: browse sessions, start new ones, and talk to the agent on E Ink.

> Status: prototype, running on a Kompakt (MuditaOS K, Android 12, 480×800) against
> Hermes Agent 0.23.4.

## Scope: chat only

This app is the conversation half. **Notifications come from a separate app**,
[ntfy Kompakt](https://github.com/zenibako/ntfy-android) (a fork of ntfy-android with an
MMD-style E Ink theme; see its [PR #1](https://github.com/zenibako/ntfy-android/pull/1)).
An earlier version of this sample had its own ntfy listener, Inbox and urgent alert; they
were removed in favour of that fork so the phone has one notification path.

**E Ink.** Agent replies are rendered once, when the run completes, not token by token.
Only "Working: <tool>…" status lines update during a run. Lists refresh on a button, not on a
timer. All UI comes from MMD (`ThemeMMD`, `TopAppBarMMD`, `LazyColumnMMD`, `TextFieldMMD`,
`ButtonMMD`, `HorizontalDividerMMD`).

## Architecture

```
 Kompakt                                      Cloud host
 ┌─────────────────────────┐   HTTPS + key   ┌──────────────────────────────┐
 │ Hermes (this app)       │ ──────────────► │ Hermes API server :8642      │
 │   Sessions / Chat       │ ◄── SSE ─────── │  /api/sessions, …/chat/stream│
 │                         │                 │                              │
 │ ntfy Kompakt (fork)     │ ◄── JSON stream │ ntfy topic ◄── Hermes ntfy   │
 │   priority 5 → full-    │                 │   adapter, cron `deliver`,   │
 │   screen alert          │                 │   `hermes send ntfy:…`       │
 └─────────────────────────┘                 └──────────────────────────────┘
```

Talking to the agent uses the Hermes API server's session endpoints:
`GET /api/sessions`, `POST /api/sessions`, `GET /api/sessions/{id}/messages`,
`POST /api/sessions/{id}/chat/stream` (SSE: `assistant.delta`, `tool.*`,
`hermes.tool.progress`, `run.completed` / `run.failed`), and
`POST /api/sessions/{id}/fork`.

## Notifications

Install ntfy Kompakt, subscribe to the topic Hermes publishes to (`NTFY_TOPIC`) with instant
delivery, and sign in if the server needs it. Messages land in ntfy's list; **priority 5**
messages also open a full-screen, black-on-white alert (Open / Dismiss), the alarm-style path
MuditaOS K shows on screen. Everything else stays quiet.

To have the agent use it, tell Hermes when to send priority 5, for example in `SOUL.md`:
*"Only use ntfy priority 5 for things that need me within the hour; everything else is
priority 3."* (Hermes 0.22's ntfy adapter can't set a priority itself; a small script that
POSTs with `Priority: 5` and the `hermes-agent` tag fills that gap.)

## Hermes host setup

`~/.hermes/.env`:

```
API_SERVER_ENABLED=true
API_SERVER_KEY=<long random secret>
# API_SERVER_HOST defaults to 127.0.0.1. Keep it there and expose it via Tailscale
# (tailscale serve) or a TLS reverse proxy rather than binding 0.0.0.0.
```

Then `hermes gateway restart`.

## App setup

Install the APK (`./gradlew :samples:hermes-kompakt:assembleDebug`, then `adb install`),
open it, and fill in the Hermes URL and API key (see [Profiles](#profiles) for more than
one profile). URLs without a scheme get `https://`. "Test" calls `/health` and shows the
HTTP code or error if it fails.

## Sessions

* **New** starts an API session titled "Kompakt <date time>" (Hermes requires unique titles).
* Sessions from other sources (cron, kanban, messaging) are listed as **read-only**: Hermes
  only lets API clients continue `api_server` and `bot_room` sessions. Opening one shows its
  history and a **Continue here** button that forks it into a new API session.
* Sending closes the keyboard so the reply gets the whole screen.

## Profiles

Each [Hermes profile](https://hermes-agent.nousresearch.com/docs/user-guide/profiles) has its
own sessions, memory and `API_SERVER_KEY`. The app talks to one gateway URL:

* The **default profile** uses the root routes (`/api/...`, `/v1/...`) and the key entered
  in "API key".
* **Other profiles** are added under Settings > Other profiles with their name and their own
  key. Requests go to `/p/<profile>/api/...`, which needs the gateway to serve profiles from
  one listener (`gateway.multiplex_profiles`). A named prefix rejects the default key with
  `401`. `/health` always stays at the root.
* To skip typing long keys on the phone, run `tools/import-profiles.py` on a computer that can
  SSH to the Hermes host and reach the phone over adb. It reads each
  `HERMES_<NAME>_GATEWAY_API_KEY` from the server's `.env`, checks which slug answers, and
  pushes a one-time import file that the app merges on its next start and then deletes.
  Keys are never printed.

The sessions screen's title and the label on agent replies show the profile's name, taken
from the model id in `GET /v1/models` (the profile name, `API_SERVER_MODEL_NAME` if set, or
`hermes-agent` for the default profile, shown as "Hermes"). To give a profile a friendlier
label, set `API_SERVER_MODEL_NAME` for that profile on the server. When more than one profile
is configured, the name in the top bar opens a menu of profiles; picking one reloads its
sessions. A chat stays on the profile it was opened from.

When a run stops to ask before running a tool (the session stream's `approval.request`), the
chat shows an inline card naming the tool and what it will run, with Deny and Approve. Where
Hermes offers more than one way to approve (once, for this session, always), Approve opens a
choice sheet. The answer goes to `POST /v1/runs/{id}/approval`: `{request_id,
execution_generation, choice}` when the event carries a generation, otherwise `{choice}` with
the `request_id`. A prompt already answered elsewhere is reported as such, not as an error.

## Next steps

* "Stop" for a running turn (`POST /v1/runs/{id}/stop`).
* Hide system-injected prompts (cron skill instructions) in read-only histories.
* Move secrets to the Android Keystore.
