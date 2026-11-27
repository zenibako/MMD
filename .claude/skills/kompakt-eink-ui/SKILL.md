---
name: kompakt-eink-ui
description: Design and build Mudita Kompakt E Ink app UI that looks native next to the stock apps (MMD look), in Compose with MMD, Android XML views or CSS (WebView apps such as an Obsidian theme), and audit it against the phone. Use when designing, building or reviewing any Kompakt or MMD screen.
---

# Kompakt E Ink UI

These rules make a third-party app look and behave like the Kompakt's own apps. The
numbers come from the stock apps themselves (MuditaOS K build 20261116): decoded resources
of Messages, Contacts, Phone, Calendar and Alarm, and on-device measurements of Settings,
Notes, Calendar, Alarm, Meditation, Weather and the launcher. Where the stock apps have no
answer (switching profiles, per-item actions, agent activity, read-only items), the patterns
come from the Hermes app, built and used on the phone. See
`docs/kompakt-ui-audit.md` in the MMD repo (zenibako/MMD) for the evidence and for how the Hermes,
Twofold and ntfy Kompakt apps compared.

This file holds the rules and numbers. Read the reference that fits the task:

| Task | Read |
|---|---|
| Building or reviewing Compose UI (anything using `ThemeMMD`, `*MMD` components, `@Composable`) | [references/compose.md](references/compose.md): the drop-in `KompaktUi.kt`, component choices, screen recipes |
| Theming or reviewing an Android XML-view app (layouts, `themes.xml`, Material Components, RecyclerView, Preferences), usually as a build flavor | [references/xml.md](references/xml.md): theme items, drawables, layouts |
| Theming a WebView or CSS app for the Kompakt (an Obsidian theme, a web page, an Electron/Capacitor app) | [references/web.md](references/web.md): units, border rounding, embedded fonts, scoping to the phone |
| Checking a rule or an app against the real phone, over adb | [references/audit.md](references/audit.md): how to decode stock APKs, measure screens and drive the phone safely |

For a design question with no code, this file is enough.

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
labels and buttons. MMD ships no Lato Medium; use its `lato_semi_bold` for 500, because
Regular reads visibly lighter than the stock apps on the panel (seen on the phone in the
Obsidian theme). The Mudita kit's scale:

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
  arrow. Stock bars are title-only. A small 18 sp Medium line above the title is not a stock
  pattern; use it only for context the user needs on every visit and can't get from the
  title (Hermes' chat: the profile, plus the model when the chat overrides the default).
- **Title as a switcher** (Hermes' profiles): when a screen shows one of a few peers
  (accounts, profiles), the title is the switcher. The whole title is a 48 dp target with a
  small ▾ after the name, and it opens an outlined dropdown anchored under the title:
  names in 21 sp, wrapping to two lines, the current one Black with a check. This is the
  one accepted dropdown: picking a peer is quick, frequent and keeps the list in view. Only
  with two or more peers; with one, the title is plain. Don't add a separate switch icon.
- Back arrow on sub-screens only (left, 48 dp target).
- Actions: plain black icons, about 28 dp in 48 dp targets, at most two or three.
  Further actions (Refresh, Filter, Settings) go in a bottom sheet behind ⋮, which leaves
  the width to the title.
- The screen's one create action ("+", new message) is a **filled black rounded rectangle,
  42 x 36 dp with 8 dp corners**, a 22 dp white "+" drawn with 2 dp strokes, 16 dp from the
  right edge. Not a round FAB. (Calendar's alternative: a 64 dp outlined square "+", 2 dp
  stroke, at the bottom right.)
- Exceptions seen in stock: a main screen that is one big glanceable view (Weather,
  Meditation) may drop the rule and float its icons over the content.

### Lists

- Rows start text at 16 dp. Heights from the table above; vertical padding about 10 dp.
  Rows keep their own right margin (about 12 dp): the scrollbar column only appears when
  the list overflows, so a short list would otherwise run the time into the edge.
- Single-line row: 21 sp Medium label, an optional value or ">" chevron at the right.
- Two-line row: 23 sp Black title over a 21 sp Medium summary. Message lists: bold name
  with the time at the right, one line of preview.
- A dashed divider under every row, from the text start to the right edge (it stops at the
  scrollbar column when there is one).
- Long lists: ▲/▼ buttons with the pill scrollbar in a column at the right (the list stops
  56 dp short of the edge). ▲ is faint at the top, ▼ at the bottom. MMD's
  `LazyColumnMMD` draws exactly this by default.
- Selection mode (Messages): a 20 dp checkbox appears at the start of every row and the
  top bar shows the delete action. No highlight fill. Use it for acting on many rows.
- Per-row actions (pin, delete, rename one item): tap opens the row, **long-press opens an
  item sheet** titled with the row's name. Destructive actions then ask in a confirm sheet.
- Pinned rows sort first and show a 20 dp line pin before the name. A row the user can't
  change shows a small (18 dp) lock, plus the icon of what owns it, before its preview.
- Filtering a list: ⋮ > Filter opens a "Show" sheet of switches, one per kind of row, all
  on by default and saved. The ⋮ entry reads "Filter (some hidden)" while any are off, so a
  short list is never a mystery.
- Untitled items get a placeholder title ("New chat"), never a blank row.
- Dates and times are day-relative and change only when the date does: the time for
  today, "Yesterday", the weekday within the last 6 days, then "23 Nov" (with the year
  when it isn't this year). Never "5 min ago": a ticking label redraws the panel for
  nothing. Take the day words from the system (`DateUtils`), follow the phone's locale and
  12/24-hour setting, and refresh today's date on resume and on a date, time or time-zone
  change, with no timer. Messages inside a conversation show the time only, under day markers.
- Metadata on a row goes on one 18 sp line joined with " · " ("Nov 26, 2026 · 5 min").
  Give the title the ellipsis, not the metadata, and leave a value out rather than show a
  placeholder when it isn't known yet.
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
- Open a conversation at the **end of the newest message**. New messages scroll the view
  only while the reader is at the bottom, so scrolling up to read is never undone; sending
  always shows your message.
- Agent or system activity between messages: one 18 sp line, no bubble, inset like the
  bubbles ("Used web_search, read_file"; names only, merged). The full tool calls and
  system entries stay behind a switch in the conversation's ⋮ sheet, off by default; when
  shown, each is an 18 sp Black label over at most four lines, with a dashed divider.
- A conversation the user can't write to: replace the compose bar's content with a lock,
  the icon of what owns it, and at most one outlined action at the right (Hermes: a pencil,
  "Continue here", which copies it into a writable one). Never a greyed-out field.
- A failed load: the status line says what failed, with a Retry icon at its right.
- A per-conversation setting (Hermes: the model) goes in the conversation's ⋮ sheet as
  "Model: <current>", opening a choice sheet.

### Bottom sheets (all ⋮ menus and choices)

The stock apps have **no popup or dropdown menus** and no centred alert dialogs on the
paths we could reach. ⋮, long-press actions and confirmations open a bottom sheet (the
title switcher above is the only dropdown):

- Full width, white, a **3 dp black top border**, no radius, no shadow, no scrim (the
  screen above stays fully visible). Appears without animation. Back and a tap above the
  sheet close it.
- A centred 25 sp Black title.
- Full-width stacked buttons, 12 dp side margins, 16 dp gaps: each action is an
  **outlined** 48 dp button (56 dp in Messages) with a centred 21 sp Black label, and the
  last one is a **filled black Cancel**.
- A destructive action is named in its button ("Delete conversation"), not coloured.

Kinds of sheet, all with that frame:

| Sheet | Title | Content | Closing button |
|---|---|---|---|
| Menu (⋮) | "Menu", or the screen's subject ("Chat") | outlined actions | filled Cancel |
| Item (long-press) | the item's name | outlined actions ("Pin to top", "Delete") | filled Cancel |
| Confirm | the question ("Delete this session?") | the item's name as a centred 21 sp line, then the action as the **filled** button | **outlined** Cancel, so only one button is black |
| Toggles | what they control ("Show") | 48 dp rows: 21 sp label, switch at the right, whole row toggles; changes apply at once | filled "Done" |
| Choice | the setting ("Model") | full-width outlined 48 dp options, label at the start; the current one 3 dp, Black, with a check; picking applies and closes | filled Cancel |

- A choice sheet suits up to about six options. Longer lists page inside the sheet with
  the same ▲/▼ and pill scrollbar as any list (never a free-scrolling column), sized to
  their rows up to about 320 dp so the screen's top stays visible; past a dozen, use a
  radio sub-screen instead. Keep the default option ("Profile default") outside the list,
  so it is there even when the list fails to load. While options load or after they fail,
  show one line of text in their place.
- When a pick has to be accepted by a server, show "Saving…" in the sheet, mark the new
  option current only once it is accepted, and show a refusal as one line in the sheet,
  which stays open.
- Sheets chain, never stack: an action that needs another sheet closes the first.
- A menu entry that opens a setting shows its current value ("Model: Profile default").

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
- Split a look into separate settings rather than offering bundled styles: one choice
  for the main thing (a radio sub-screen or choice sheet) plus switches for the
  independent parts (Twofold: Marks, then Rounded corners and Dark board). Previews in
  the choice follow the switches. When replacing a bundled setting, map the saved old
  value onto the new ones.

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
- Small adjustments (text scale): outlined − and + buttons (2 dp, 48 dp targets) either
  side of the current value, in a toggle-style sheet that applies each step at once and
  closes with Done. Never borderless glyphs, and the unavailable end goes faint.

### Showing levels without grey

The panel has few grey levels and ghosts, so grey fills don't tell values apart.

- Tell levels apart with marks: weight of outline, frames, dots or steps (Twofold adds a
  white dot per doubling to its solid tiles), and wrap marks onto a second row rather than
  let them run past the shape.
- Empty slots are a 1 dp dashed outline, not a grey fill.
- Inverse (black fill, white text or marks) is the strongest level; keep it for the
  highest or current one.

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
- Errors as one line of text in the content area, not a coloured snackbar. Say what
  failed and, for a network failure, the likely fix in a few words ("Couldn't load. Can't
  reach Hermes; check Tailscale."), never a stack trace or host list.
- Show friendly names for ids ("system-admin" as "System Admin"), and let the user rename
  them in Settings. Display only: keep ids in storage and requests.

## Review checklist

Run this on every screen, then the grep list in the Compose, XML or web reference.

Every "no" is a defect. Mark an item n/a when the screen has no such element (a settings
screen has no create action), and keep the exceptions above (a Weather-style main screen
may drop the bar's rule).

- [ ] Only black and white, apart from a faint disabled glyph?
- [ ] Lato with Black titles (25 sp in the bar) and Medium content (21 sp rows)?
- [ ] No ripple, animation, transition, shadow or elevation anywhere, including sheets?
- [ ] Top bar 64 dp, title-only (plus back and up to three icons), 3 dp rule below?
- [ ] Create action is the black 42 x 36 rounded rectangle, not a FAB?
- [ ] Text at 16 dp; dashed 1 dp (2/2) dividers from the text start to the edge?
- [ ] Row heights 64 / 72 / 81 dp as the row type needs?
- [ ] ⋮, long-press actions and confirmations are bottom sheets with stacked outlined
      buttons and one black button, not popups or centred dialogs? The only dropdown is a
      title switcher.
- [ ] Destructive actions confirm in a sheet that names the item?
- [ ] Conversations open at the newest message and don't jump while the reader scrolls up?
- [ ] Read-only items say so with a lock and offer a way forward, not a disabled field?
- [ ] Failures are one plain line, with Retry where retrying helps?
- [ ] Buttons 48 dp tall with 12 dp corners; switch 48 x 30; radio 26; checkbox 20?
- [ ] Long lists use ▲/▼ with the pill scrollbar?
- [ ] Dates day-relative and never "minutes ago"; metadata on one " · " line?
- [ ] Levels and empty slots told apart by marks and dashes, not grey fills?
- [ ] Secondary tasks open as sub-screens with a back arrow, and system back works?
- [ ] No auto-refresh; an explicit Refresh where data is remote?
- [ ] Fits 360 dp wide; nothing hidden under the 36 dp status bar?
- [ ] Light theme even with the system dark setting on?

To audit against the phone, see [references/audit.md](references/audit.md).
