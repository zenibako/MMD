# Auditing against the stock Kompakt apps

Use this to check a rule in `SKILL.md`, or one of our apps, against the phone's own apps.
The last full audit (November 2026, build 20261116) is `docs/kompakt-ui-audit.md` in the
MMD repo; start there before measuring anything again.

## What lives where

- Messages, Contacts and Phone are AOSP forks built with Views. Their exact values are in
  resources, in a shared Mudita XML kit: `Mudita<Role><Size><Weight>` text styles,
  `KompaktButton.*` styles, `line_dashed`, `bg_dashed`, `bottom_dialog_bg`,
  `*_msg_bubble_bg`, `toolbar_background`, `kompakt_switch_selector`.
- Calendar and Alarm are mixed; their `dimen_app_bar_*` values are in resources.
- Notes, Settings, the launcher, Meditation and Weather are R8-minified Compose. Their
  values are in code, so measure them on screen.

## Steps

1. Pull the stock APK (`adb shell pm path com.mudita.<app>`, then `adb pull`). This is
   read-only. Decode its resources (apktool, aapt2, or a small `resources.arsc` reader) and
   read `values/dimens.xml`, `styles.xml` and the `drawable/` shapes. Messages, Contacts,
   Phone, Calendar and Alarm keep their values there; the Compose apps (Notes, Settings,
   launcher, Meditation, Weather) keep them in code, so measure those on screen.
2. To measure on screen: `adb exec-out screencap -p > s.png` and
   `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml`. Divide pixel
   bounds by 1.33 for dp. Anti-aliased corners read about 2 px small.
3. **Driving the owner's phone safely.** One input per step; screenshot and check
   `dumpsys activity activities | grep mResumedActivity` before every tap. Open screens
   with `am start` or `monkey -p <pkg>` instead of tapping where possible. Never tap inside
   a Phone or Messages conversation or a call-log row (call and send buttons live there).
   Never toggle, pick, start, save or delete; cancel every sheet. To connect, and when the
   wireless-debugging port has changed, follow the `kompakt-wireless-debugging` skill.
4. Personal content appears on screen. Record layout only, never content, and delete the
   screenshots, `/sdcard/ui.xml` and the local `ui.xml` copy when done.
5. Record findings as: screen, token, stock value, our value, verdict. Update `SKILL.md`
   when the stock apps show a better rule.
