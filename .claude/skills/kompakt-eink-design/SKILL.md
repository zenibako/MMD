---
name: kompakt-eink-design
description: Design rules for E Ink apps on the Mudita Kompakt that look native next to its stock apps (MMD look). Use when designing, building or reviewing any Kompakt or MMD screen, in Compose or Android XML.
---

# Kompakt E Ink design

These rules make a third-party app look and behave like the Kompakt's own apps. The
numbers come from the stock apps themselves (MuditaOS K build 20261116): decoded resources
of Messages, Contacts, Phone, Calendar and Alarm, and on-device measurements of Settings,
Notes, Calendar, Alarm, Meditation, Weather and the launcher. See
`docs/kompakt-ui-audit.md` in the MMD repo (zenibako/MMD) for the evidence and for how the Hermes,
Twofold and ntfy Kompakt apps compared.

Implementation skills carry these numbers into code:

- Compose with MMD: `kompakt-eink-compose`
- Android XML views: `kompakt-eink-xml`

Mudita's own Compose apps appear to be built on MMD (they ship MMD's exact Lato files), but
they **override several MMD defaults**: heavier type, dashed dividers, taller buttons, a
smaller switch, and bottom sheets instead of dropdown menus. Follow the stock values here,
not MMD's defaults, wherever the two differ. When a stock app and this file disagree, the
stock app wins: update this file.

## The device

- Mudita Kompakt, MuditaOS K (Android 12, API 31). 480 x 800 px at 213 dpi, so
  **1 dp = 1.33 px** and the screen is **360 x 600 dp**. The status bar takes 36 dp, so
  an app gets about 564 dp of height.
- E Ink: few grey levels, slow refresh, ghosting. Every redraw is visible and costs battery,
  so the screen should change only when the content changes.
- No notification tray and no Play Services. Alarms, calls and calendar events show full
  screen; a sideloaded app may do the same with a full-screen intent for urgent items only.

## Principles

1. **Black on white.** `#000000` on `#FFFFFF`. No colour anywhere (the stock UI's only
   colour is an AOSP leftover). Light theme only.
2. **Hierarchy by weight, not colour.** Lato Black (900) vs Medium (500) for text; stroke
   1 / 2 / 3 / 3.5 dp and filled vs outlined for shapes. Never grey text. The one grey
   allowed is a dithered or faint (30%) glyph for a disabled control, such as ▲ at the top
   of a list.
3. **No motion.** No ripples, transitions, animated sheets or popups, lift-on-scroll,
   spinners, or token-by-token streaming. Show the final state in one redraw.
4. **Flat.** No elevation and no shadows. Separate things with rules and dashed lines.
5. **Paged, not flung.** Lists page with ▲/▼ buttons and a pill scrollbar; horizontal
   content pages with ◀ ▶ arrows.
6. **Calm.** No auto-refresh or polling UI; offer an explicit Refresh. Only urgent items
   interrupt, and then full screen.

## Tokens

### Type (Lato, black text)

Stock apps use two weights: **Medium 500** for content and **Black 900** for titles,
labels and buttons. The Mudita kit's scale:

| Use | Size / weight | Kit style |
|---|---|---|
| Top bar title | **25 sp Black** | TitleMedium900 |
| Sheet title, empty-state headline | 25 sp Black | TitleMedium900 |
| Two-line row title | 23 sp Black (or 18 sp Black above a value) | TitleSmall900 / BodySmall900 |
| Single-line row label, row value or summary, body | **21 sp Medium** | BodyMedium500 |
| Settings section header | 21 sp Black | LabelMedium900 |
| Button label | 21 sp Black (23 sp on 56 dp buttons) | LabelMedium900 / LabelLarge900 |
| Message text | 18 sp Medium | BodySmall500 |
| Times, captions, metadata | 18 sp Medium (15 sp at most dense) | BodySmall500 / LabelXSmall |
| Full-screen alert title | 36 sp Black | Headline900 |

Line heights: about 1.2x the size (25/28, 23/25, 21/25, 18/22). Sentence case, except
ALL CAPS for a full-screen alert label. One line with an ellipsis for titles and row
previews. MMD's defaults (Medium only, 14 to 28 sp, top bar 24 sp) are too light and small
next to the stock apps.

### Strokes and shapes

| Thing | Value |
|---|---|
| Rule under the top bar | **3 dp solid black**, full width |
| List / settings divider | **1 dp dashed, 2 dp dash, 2 dp gap**, from the text start (16 dp, or after the row's icon) to the right edge |
| Received message outline | 1 dp |
| Button outline, sent message outline, text field outline | 2 dp |
| Bottom sheet top border | 3 dp, no radius |
| Selected message | 3.5 dp |
| Button corners | 12 dp large/medium (Compose apps measure about 10 dp), 8 dp small |
| Message bubble corners | 12 dp, with one square "tail" corner |
| Info card | 2 dp outline, 8 dp corners |

### Sizes

| Thing | Value |
|---|---|
| Top bar | 64 dp content + 3 dp rule (67 dp total) |
| Text inset | **16 dp** from the screen edge; about 60 dp when a 24 to 28 dp icon leads the row |
| Bar icons | about 28 dp glyph in a 48 x 48 dp target (10 dp padding) |
| Back arrow | about 25 dp glyph, title starts at 60 dp |
| Row heights | 64 dp single line or two short lines, 72 dp message list rows, 81 dp rows with an icon and two lines, 62 to 65 dp radio rows |
| Buttons | 56 dp large, **48 dp medium (default)**, 36 dp small |
| Switch | 48 x 30 dp pill |
| Radio | 26 dp circle |
| Checkbox | 20 dp square |
| Touch target | 48 dp minimum |
| ▲/▼ page buttons | 56 dp targets; pill scrollbar about 8 dp wide between them |

## Screen anatomy

```
┌──────────────────────────────────┐
│ ←  Title (25 Black)       ✎ ⚙ [+]│  top bar: 64 dp
├━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┤  3 dp rule
│ Row title (23 Black)          ▲  │  row: 64 dp, text at 16 dp
│ Summary (21 Medium)           ┃  │
│ - - - - - - - - - - - - - - - ┃  │  1 dp dashed, 2/2, from 16 dp
│ Row label (21 Medium)       > ┃  │
│ - - - - - - - - - - - - - - - ▼  │  ▲/▼ + pill scrollbar column
└──────────────────────────────────┘
```

### Top bar

- White, 64 dp tall, then the 3 dp black rule. No elevation, no colour change on scroll.
- Title: 25 sp Black, one line, ellipsis, starting at 16 dp, or at 60 dp after a back
  arrow. Stock bars are title-only; a small label above the title is not a stock pattern,
  so put context in the title or the first row instead.
- Back arrow on sub-screens only (left, 48 dp target).
- Actions: plain black icons, about 28 dp in 48 dp targets, at most two or three.
  Further actions go in a bottom sheet behind ⋮.
- The screen's one create action ("+", new message) is a **filled black rounded rectangle,
  42 x 36 dp with 8 dp corners**, a 22 dp white "+" drawn with 2 dp strokes, 16 dp from the
  right edge. Not a round FAB. (Calendar's alternative: a 64 dp outlined square "+", 2 dp
  stroke, at the bottom right.)
- Exceptions seen in stock: a main screen that is one big glanceable view (Weather,
  Meditation) may drop the rule and float its icons over the content.

### Lists

- Rows start text at 16 dp. Heights from the table above; vertical padding about 10 dp.
- Single-line row: 21 sp Medium label, an optional value or ">" chevron at the right.
- Two-line row: 23 sp Black title over a 21 sp Medium summary. Message lists: bold name
  with the time at the right, one line of preview.
- A dashed divider under every row, from the text start to the right edge (it stops at the
  scrollbar column when there is one).
- Long lists: ▲/▼ buttons with the pill scrollbar in a column at the right (the list stops
  56 dp short of the edge). ▲ is faint at the top, ▼ at the bottom. MMD's
  `LazyColumnMMD` draws exactly this by default.
- Selection mode (Messages): a 20 dp checkbox appears at the start of every row and the
  top bar shows the delete action. No highlight fill.
- Empty state: a centred line illustration, a 25 sp Black headline, a 21 sp Medium body,
  and one outlined 48 dp button ("Create new note").

### Conversations (Messages)

- Bubbles are white with a black outline and 12 dp corners, one square corner as the tail:
  - received: left, **1 dp**, square bottom-left;
  - sent: right, **2 dp**, square bottom-right;
  - selected: the same shape at **3.5 dp**.
- Consecutive messages from one author group with 2 dp between them; the middle ones
  square their tail-side corners, the last one rounds them again. 18 dp between authors.
- Text 18 sp Medium, padding 10 dp horizontal and 8 dp vertical. Time and status under
  the bubble, 4 dp below, same side.
- Day markers between days, centred.
- Compose bar under a rule: a plain field and an outlined send button.
- Replies from a network or agent appear once, complete. Show a one-line status meanwhile.

### Bottom sheets (all ⋮ menus and choices)

The stock apps have **no popup or dropdown menus** and no centred alert dialogs on the
paths we could reach. ⋮ and confirmations open a bottom sheet:

- Full width, white, a **3 dp black top border**, no radius, no shadow, no scrim (the
  screen above stays fully visible). Appears without animation.
- A centred 25 sp Black title.
- Full-width stacked buttons, 12 dp side margins, 16 dp gaps: each action is an
  **outlined** 48 dp button (56 dp in Messages), and the last one is a **filled black
  Cancel**.
- A destructive action is named in its button ("Delete conversation"), not coloured.

### Settings

- Rows 64 dp. Text at 16 dp, or after a 24 to 28 dp icon at about 64 dp.
- Section header: 21 sp Black, with a dashed divider from 16 dp.
- Switch row: 21 sp Medium label (or 23 Black title over 21 Medium summary), the 48 x 30
  pill switch at the right. Off: white pill, black outline, black knob left. On: black
  pill, white knob right. No animation.
- Navigation row: label with ">" at the right; opens a full sub-screen with a back arrow.
- Single choice: a sub-screen of 62 dp radio rows, 26 dp radio at 16 dp, label at about
  61 dp, dashed dividers from the label. Picking applies at once.
- Dashed divider under every row.

### Buttons and fields

- Primary: filled black, white 21 sp Black label. At most one per screen; the filled
  Cancel in a sheet is the exception.
- Secondary: white, 2 dp black outline.
- Height 48 dp by default (56 dp large, 36 dp small), 12 dp corners (8 dp small).
- Text and date fields: 64 dp tall, 2 dp outline, about 10 dp corners.

### Paging and steppers

- Horizontal content (hourly forecast, days): ◀ ▶ arrows at the ends, the unavailable one
  faint. Calendar shows a column of small rounded pills as a page indicator.
- Numeric choice (durations): ◀ value ▶ inside a 3 dp outlined circle.

### Full-screen urgent alert

- Only for items the user asked to be interrupted for.
- White, 24 dp padding, "URGENT" label (ALL CAPS, 18 sp Black), a 36 sp Black title, the
  scrollable body, then a full-width filled "Open" and an outlined "Dismiss", both 56 dp.

### Icons

- Line icons in black, matching the stock pencil, sliders, back arrow and "+" (2 dp
  strokes, round caps). Bar icons about 28 dp; inline status icons 20 to 24 dp.
- Material Symbols *outlined* is the closest public set. Use MMD's `chevron_*` drawables
  for directions.

### Copy

- Short, plain, sentence case. Name the place ("Settings", "Display").
- Errors as one line of text in the content area, not a coloured snackbar.

## Review checklist

Every "no" is a defect.

- [ ] Only black and white, apart from a faint disabled glyph?
- [ ] Lato with Black titles (25 sp in the bar) and Medium content (21 sp rows)?
- [ ] No ripple, animation, transition, shadow or elevation anywhere, including sheets?
- [ ] Top bar 64 dp, title-only (plus back and up to three icons), 3 dp rule below?
- [ ] Create action is the black 42 x 36 rounded rectangle, not a FAB?
- [ ] Text at 16 dp; dashed 1 dp (2/2) dividers from the text start to the edge?
- [ ] Row heights 64 / 72 / 81 dp as the row type needs?
- [ ] ⋮ and confirmations are bottom sheets with stacked outlined buttons and a black
      Cancel, not popups or centred dialogs?
- [ ] Buttons 48 dp tall with 12 dp corners; switch 48 x 30; radio 26; checkbox 20?
- [ ] Long lists use ▲/▼ with the pill scrollbar?
- [ ] Secondary tasks open as sub-screens with a back arrow, and system back works?
- [ ] No auto-refresh; an explicit Refresh where data is remote?
- [ ] Fits 360 dp wide; nothing hidden under the 36 dp status bar?
- [ ] Light theme even with the system dark setting on?

## Auditing against the stock apps

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
   Never toggle, pick, start, save or delete; cancel every sheet. Wireless-debugging ports
   change often; ask the owner for the current one.
4. Personal content appears on screen. Record layout only, never content, and delete the
   screenshots and `/sdcard/ui.xml` when done.
5. Record findings as: screen, token, stock value, our value, verdict. Update this skill
   when the stock apps show a better rule.
