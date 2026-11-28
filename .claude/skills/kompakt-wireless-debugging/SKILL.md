---
name: kompakt-wireless-debugging
description: Connect to a Mudita Kompakt over wireless adb (Tailscale or LAN), keep a fixed port, install builds, read logs and screens, and share the phone safely between sessions. Use whenever a task needs adb on the Kompakt, or adb stops connecting.
---

# Kompakt wireless debugging

The Kompakt has no USB data workflow worth using day to day, so development runs over
Android's Wireless debugging. Its pain points are a port that keeps changing, several
sessions sharing one phone, and builds signed on different machines. This skill covers all
three.

## Connecting

The phone's address comes from `KOMPAKT_HOST` (its Tailscale IP, so it works away from home)
and optionally `KOMPAKT_LAN_HOST` (its Wi-Fi IP). Keep them in the shell profile on each
build machine, not in the repo. Ask the owner for the address if neither is set and project
memory doesn't have it.

Use [scripts/kompakt-adb](scripts/kompakt-adb). It tries, in order: `HOST:PORT` if given,
`HOST:5555`, `LAN_HOST:5555`, then the port Wireless debugging advertises over mDNS (same
network only, and only for the phone it saw at that host before). It prints the serial to
use with `adb -s`, or exits nonzero.

```sh
S=$(kompakt-adb) || exit 1                   # uses KOMPAKT_HOST
adb -s "$S" shell getprop ro.build.version.release
```

Install it once per machine so every session finds it:

```sh
ln -sf "$PWD/.claude/skills/kompakt-wireless-debugging/scripts/kompakt-adb" ~/.local/bin/kompakt-adb
```

(`~/.local/bin` must be on `PATH`; `/opt/homebrew/bin` works on a Mac.)

### Fixed port 5555

Wireless debugging picks a new random port whenever it's turned on, after a reboot, and
sometimes after the phone sleeps or changes network. Port 5555 avoids asking for the port
again, but it is a different listener: `adb tcpip 5555` turns on legacy adb over TCP,
which has **no TLS and no pairing**. Anyone who can reach the phone on 5555 can get a shell
once the phone has trusted their key, and the traffic can be read on the way.

- Use 5555 only over an encrypted, private path: Tailscale (WireGuard) to the phone's
  tailnet address, or a trusted home network you control. Never on public or shared Wi-Fi.
- Otherwise stay on the TLS Wireless debugging port and look the port up each time.
- Turning Wireless debugging off, or a reboot, closes 5555 again.

To pin it:

```sh
kompakt-adb --persist "$KOMPAKT_HOST:<port from the Wireless debugging screen>"
```

Without the script it's three commands:

```sh
adb connect HOST:PORT
adb -s HOST:PORT tcpip 5555
adb connect HOST:5555
```

5555 lasts until the phone reboots or Wireless debugging is turned off. Then the owner reads
the new port from Settings > Developer options > Wireless debugging once, and you run
`--persist` again. If a sandbox or permission check refuses `adb tcpip`, give the owner the
three commands instead of looking for a way around it.

### First pairing on a new machine

On the phone: Wireless debugging > Pair device with pairing code. Then
`adb pair HOST:PAIRPORT CODE` (the pairing port differs from the connect port), and connect
as above. Each machine pairs once; the pairing survives reboots.

### When it won't connect

| Symptom | Likely cause and fix |
|---|---|
| `failed to connect` / `Connection refused` on 5555 | Phone rebooted or Wireless debugging was toggled: get the new port, run `--persist` |
| Times out | Tailscale is off on the phone or the Mac, or the phone is asleep: wake it, check `tailscale status` |
| `unauthorized` | This machine's pairing was revoked: pair again |
| `offline` | Stale connection: `adb disconnect HOST:PORT` for that serial only, then connect |
| mDNS finds nothing | It only works on the same Wi-Fi, not over Tailscale |

## Sharing the phone

Several sessions and the owner use the same phone, often at the same time.

- Always pass `-s "$S"`. Never run `adb kill-server`, `adb disconnect` without a serial, or
  `adb tcpip` while another session is mid-task on the current port; tell the owner instead.
- Before any input, take a screenshot and check what is in front:
  `adb -s "$S" shell dumpsys activity activities | grep mResumedActivity`. If it isn't the app
  you expect, or the screen changed without your input, stop and say so: another session or
  the owner is using the phone.
- One input per step. Open screens with `am start -n <pkg>/<activity>` or
  `monkey -p <pkg> 1` rather than tapping through other apps.
- Never tap inside Phone or Messages conversations or call-log rows; call and send buttons
  sit where other apps keep harmless icons. Don't toggle, save or delete anything in an app
  you aren't working on.
- Leave the phone in the app you found it in, or tell the owner where you left it.

## Installing builds

- `adb -s "$S" install -r app-debug.apk`. Debug builds are signed with the building
  machine's `~/.android/debug.keystore`, so **build on the machine that built the installed
  copy**. A different machine fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- **Never uninstall to get past a signature or version error.** It erases the app's data
  (saved games, settings, accounts). Stop and ask the owner. Copying one machine's
  `debug.keystore` to the other is the lasting fix, and that's the owner's call.
- To try a build next to the real app (an older source, a different key), give the debug
  build `applicationIdSuffix = ".preview"` and `versionNameSuffix = "-preview"`, as a local
  uncommitted change. It installs beside the real app and starts empty.
- A changed `applicationId` is a new app: nothing carries over. For a debuggable app,
  move the data with `run-as`: copy `shared_prefs/` (and `databases/` or `files/` if used)
  from the old package to the new one while both are stopped.
- An install over a newer `versionCode` fails as a downgrade. Find the newer source rather
  than forcing it.

## Looking and logging

- Screenshot: `adb -s "$S" exec-out screencap -p > shot.png` (480 x 800 px; 1 dp = 1.33 px).
  Wait about a second after an input so E Ink has finished refreshing.
- Layout: `adb -s "$S" shell uiautomator dump /sdcard/ui.xml`, pull it, then delete both copies.
- Logs for one app: `adb -s "$S" logcat --pid=$(adb -s "$S" shell pidof -s <pkg>)`.
- WebView apps (Obsidian, Capacitor): `adb -s "$S" shell cat /proc/net/unix | grep devtools`
  to find `webview_devtools_remote_<pid>`, then
  `adb -s "$S" forward tcp:9222 localabstract:webview_devtools_remote_<pid>` and open
  `chrome://inspect` or `http://localhost:9222/json`.
- Screens show personal content. Record layout, not content, and delete screenshots when
  the task is done.
- Android's animation scales (Developer options) change every app. Ask before setting them:
  `adb -s "$S" shell settings put global window_animation_scale 0`, and the same for
  `transition_animation_scale` and `animator_duration_scale`.

For UI rules and auditing against the stock apps, see the `kompakt-eink-ui` skill.
