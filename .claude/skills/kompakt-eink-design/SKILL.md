---
name: kompakt-eink-design
description: Design rules for E Ink apps on the Mudita Kompakt (MMD look). Use when designing, building or reviewing any Kompakt or MMD screen, in Compose or Android XML.
---

# Kompakt E Ink design (MMD look)

These rules make a third-party app look and behave like the Kompakt's own apps (Messages,
Contacts, Settings and the rest). They were distilled from three apps that copied the stock
Messages app: the Hermes chat sample and the Twofold game in this repo (Compose + MMD), and
the ntfy Kompakt fork (Android XML views). The numbers are the shared contract; the
implementation skills carry them into code:

- Compose with MMD: `kompakt-eink-compose`
- Android XML views / Material Components: `kompakt-eink-xml`

When a rule here and a stock app disagree, the stock app wins: record the difference and
update this file.

## The device

- Mudita Kompakt, MuditaOS K (Android 12, API 31). Screen 480 x 800 px at 213 dpi, which is
  **360 x 600 dp** (1 dp = 1.33 px). Design for that frame first; every screen must work
  with no scrolling for its main task where it can.
- E Ink: about 16 grey levels, slow refresh, ghosting. Every redraw is visible and costs
  battery, so the screen should change only when the content changes.
- No notification tray and no Play Services. Alarms, calls and calendar events show full
  screen; a sideloaded app may do the same with a full-screen intent for urgent items only.

## Principles

1. **Black on white, nothing else.** Pure `#000000` ink on `#FFFFFF` paper. No greys, no
   tints, no colour. Light theme only: dark mode maps to the same light look.
2. **Contrast by weight, not colour.** Show emphasis, state and hierarchy with bold type,
   stroke thickness (1 / 1.5 / 2 / 3 dp) and fill (outline vs solid black). Never with grey.
3. **No motion.** No ripples, transitions, animated popups, lift-on-scroll, shimmer,
   spinners that spin forever, progress that redraws many times a second, or token-by-token
   streaming. Show the final state in one redraw.
4. **Flat.** No elevation and no shadows (they render as grey smudges). Separate things
   with rules and outlines.
5. **One task per screen.** A secondary task opens a full sub-screen with a back arrow, not a
   bottom sheet, side drawer or expanding panel.
6. **Calm by default.** No auto-refresh or polling UI. Offer an explicit Refresh. Only urgent
   items may interrupt, and then full screen.

## Tokens

### Colour

| Role | Value |
|---|---|
| Ink (text, icons, rules, outlines, filled buttons) | `#000000` |
| Paper (backgrounds, text on filled buttons) | `#FFFFFF` |
| Disabled icon or text | Ink at 30% alpha (the one allowed grey; disabled only) |
| Keyboard focus overlay (XML) | `#52000000`, static |

Every Material colour role maps to one of these two (MMD's `eInkColorScheme`).

### Type

Lato everywhere (MMD ships it). Medium weight for body, **Bold** for titles and anything
the eye should land on first. MMD's scale:

| Style | Size | Use |
|---|---|---|
| headlineLarge | 28 sp | Full-screen alert title |
| titleLarge | 24 sp | Top bar title (Bold) |
| titleMedium | 20 sp | List row title (Bold) |
| titleSmall | 16 sp | Settings section header (Bold) |
| bodyLarge | 20 sp | Message text, menu items, main body copy |
| bodyMedium | 18 sp | Row second line, day markers |
| bodySmall | 15 sp | Top bar label, times, captions |
| labelLarge | 18 sp | Buttons |

Sentence case for everything except the full-screen alert label (ALL CAPS). One line with
an ellipsis for titles and row previews.

### Strokes, shapes and sizes

| Thing | Value |
|---|---|
| Bar rule (under top bar, over bottom bar, selection bar) | 3 dp solid black |
| List and settings divider | 1 dp dashed (4 dp dash, 3 dp gap), inset 20 dp start, 8 dp end |
| Received / normal outline | 1 dp |
| Outlined icon button | 1.5 dp, 9 dp radius, 40 dp square |
| Outlined text button (MMD) | 2 dp, 8 dp radius |
| "Mine" / emphasised outline | 2.5 dp |
| Selected item, board frame, dialog frame | 3 dp |
| Message bubble radius | 14 dp (received: square bottom-start corner) |
| Menu / popup | white, 1.5 dp outline, 10 dp radius, no shadow |
| Primary bar action (the "+") | 48 dp filled black rounded square, 10 dp radius, white 30 dp icon |
| Touch target | 48 dp minimum |
| Bar icon | 30 dp in a 48 dp target |
| Icon inside a button | 24 dp |
| Inline status icon | 20 dp |
| Row text inset | 20 dp start |

## Screen anatomy

```
┌──────────────────────────────────┐
│ ←  Label (bodySmall)      ⟳ ⋮ [+]│  top bar, min 64 dp
│    Bold title (titleLarge)       │
├━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┤  3 dp rule
│  Bold row title          4:19 PM │  row: 12 dp top/bottom, 20 dp start
│  One-line preview…               │
│  - - - - - - - - - - - - - - - - │  dashed divider, inset
│  …                               │
├━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┤  3 dp rule (only when there is a bottom bar)
│  Message…                  [ ➤ ] │  bottom bar: 12 dp h / 8 dp v padding
└──────────────────────────────────┘
```

### Top bar

- White, at least 64 dp tall, 8 dp horizontal padding, closed by the 3 dp rule.
- Left: a back arrow (30 dp icon, 48 dp target) on sub-screens only. The root screen has
  none.
- Middle: an optional small label (bodySmall) above a **bold title** (titleLarge, one line,
  ellipsis), 8 dp horizontal padding. The label gives context ("Twofold", the contact's
  number, the profile name); the title says where you are.
- Right: plain black icon actions (no outlines), most used first. At most three plus the
  ⋮ overflow; everything else goes in the overflow menu. The screen's single main create
  action ("+", new message) is the filled black square, at the far right.
- No elevation, no colour change on scroll, no collapsing.

### Bottom bar (optional)

- 3 dp rule above, 12 dp horizontal and 8 dp vertical padding, 8 dp between items.
- Holds the compose field and send button, or a short row of outlined icon buttons
  (Twofold's arrow pad). Not a navigation bar with tabs.

### Lists

- Rows start 20 dp in, 12 dp top and bottom padding, 2 dp between lines.
- First line: **bold** title (titleMedium, one line) with the time or count right-aligned in
  bodySmall, 12 dp from the title, on the title's baseline.
- Second line (optional): bodyMedium, one line, ellipsis.
- Dashed divider under each row, inset like the text.
- Times: today as the short time ("4:19 PM"), older as "23 Nov".
- Unread or count badges: a filled black circle with white bold 12 sp digits.
- Selection: a 3 dp black outline around the row, never a fill or highlight colour.
- Static icon decorations (topic icons, avatars) are dropped unless they carry meaning.
- Scrolling: MMD's paged scrolling with the scrollbar for lists of rows; follow-the-finger
  scrolling with no scrollbar for one long piece of reading (a chat, an article).
- When a short, fixed list must fit on one screen (a style picker), share the height evenly
  between rows instead of scrolling, and keep each description to one line (about 20
  characters on the Kompakt).

### Conversations

- Messages are outlined, never filled. Received: left, 1 dp outline, 14 dp corners with a
  square bottom-start corner. Yours: right, 2.5 dp outline, all corners round.
- Max width 86% of the screen, 12 dp horizontal and 8 dp vertical padding inside, 16 dp
  horizontal and 6 dp vertical outside.
- The time sits under the bubble on the same side, in bodySmall, 4 dp below.
- A day marker "•  23 Nov  •" (bodyMedium, centred, 10 dp vertical padding) before the first
  message of each day.
- Compose field: no outline or underline, just text, a placeholder and a black cursor, up to
  four lines. Send: an outlined 40 dp rounded square with a hollow paper-plane icon.
- Newest at the bottom; jump to the newest when the list changes.
- Agent or network replies are added once, when complete. Show a one-line status
  ("Thinking…", "Working: search…") instead of streaming text.

### Menus

- ⋮ overflow opens a menu: white, 1.5 dp black outline, 10 dp radius, no shadow, no
  enter or exit animation, bodyLarge (20 sp) items, dashed dividers between groups.
- Per-item menus (a message's "…") use the same style.

### Selection mode

- The selection bar replaces the top bar: white, the same 3 dp rule, a bold black count and
  black icons. Never a coloured or inverted bar.

### Settings

- Bold section headers (titleSmall Bold, black).
- No icon column: rows start at the 20 dp text inset.
- A dashed divider between rows, none after the last row in a section, none above a header.
- Switches and radio buttons are MMD's (black, no animation). Text input uses
  `TextFieldMMD` / an outlined Material field with black strokes.
- Actions at the end: an outlined secondary button ("Test") next to one filled primary
  button ("Save").

### Buttons

- Primary: filled black with white text (`ButtonMMD`). At most one per screen.
- Secondary: white with a 2 dp black outline (`OutlinedButtonMMD`).
- Icon buttons: plain icons in bars; the outlined 40 dp square in bottom bars and pads.
- Directional controls use MMD's `chevron_filled_*` drawables.

### Dialogs and overlays

- A white box with a 3 dp black frame, 16 dp padding, centred over the content, no scrim
  colour and no shadow.
- Bold title (about 22 sp), body text, then the outlined secondary button and the filled
  primary button on one row. Destructive actions are spelt out in the button ("Delete 3
  messages"), not shown in red.
- Prefer undo over "Are you sure?". Make destructive actions reversible where you can.

### Full-screen urgent alert

- Only for items the user asked to be interrupted for (ntfy priority 5).
- White, 24 dp padding. "URGENT" label (labelLarge, all caps), bold headlineLarge title
  (max three lines), scrollable body with no scrollbar, then a full-width filled "Open" and
  an outlined (2 dp) "Dismiss", both 56 dp tall.

### Icons

- Material Symbols, outlined style, solid black tint. Bars: 30 dp. Buttons: 24 dp.
  Inline status: 20 dp.
- Prefer hollow, line-drawn icons (the stock send icon is a hollow paper plane). Filled
  icons are for the one primary action, drawn white on black.

### Copy

- Short, plain, sentence case. Name the thing on the screen ("Tile style", "Settings").
- Errors in one line in the content area ("Could not reach Hermes: timeout"), not in a
  red snackbar.
- No emoji in chrome.

## Behaviour checklist

Use this when building or reviewing a screen. Every "no" is a defect.

- [ ] Only black and white (plus 30% ink for disabled) anywhere on screen?
- [ ] No ripple, animation, transition, shimmer or streaming text?
- [ ] No elevation or shadow, including menus, cards, FABs, bars and dialogs?
- [ ] Top bar: optional label over bold title, plain icons, 3 dp rule below?
- [ ] Main create action is the filled black rounded square, not a round FAB?
- [ ] List rows: 20 dp inset, bold title, right-aligned time, dashed inset dividers?
- [ ] Selection shown as a 3 dp outline, and the selection bar is white?
- [ ] Menus outlined, unshadowed, unanimated?
- [ ] Secondary tasks open as sub-screens with a back arrow, and system back works?
- [ ] Touch targets at least 48 dp?
- [ ] No auto-refresh; an explicit Refresh where data is remote?
- [ ] Fits 360 x 600 dp without horizontal scrolling or clipped bars?
- [ ] Light theme only, even with the system dark setting on?

## Auditing against the stock apps

To check a rule or an app against the Kompakt's own apps over adb:

1. Connect: `adb connect <ip>:<port>` (wireless debugging; the port changes often, so ask
   the owner for the current one). Check `adb devices` lists it.
2. Open the stock app with `adb shell monkey -p <package> -c android.intent.category.LAUNCHER 1`
   (find packages with `adb shell pm list packages | grep -i mudita`). Navigate only with
   taps on list rows, tabs, ⋮ and back. Never create, edit, send or delete anything.
3. Capture: `adb exec-out screencap -p > name.png` and
   `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml name.xml`.
4. Measure from the dump's `bounds` (pixels): divide by 1.33 for dp. Measure rule
   thickness, radii and text sizes from the screenshot at 1:1.
5. The owner's own content appears on screen. Record only layout, never content, and delete
   the screenshots and dumps when done (`adb shell rm /sdcard/ui.xml` too).
6. Write findings as: screen, rule, stock value, our value, verdict (match / differs / not
   covered), and update this skill when the stock app shows a better rule.
