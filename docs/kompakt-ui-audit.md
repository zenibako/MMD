# Kompakt UI audit: our apps vs the stock apps

November 2026. Mudita Kompakt, MuditaOS K build `Kompakt:12/SP1A.210812.016/20261116`,
480 x 800 px at 213 dpi (1 dp = 1.33 px).

This compares the three apps we restyled for the Kompakt (the Hermes chat sample and the
Twofold game in this repo, and the ntfy Kompakt fork) with the phone's own apps, and records
the stock values the `kompakt-eink-ui` skill now uses.

## Method

- **Decoded resources.** The base APKs of 11 stock apps were pulled read-only and their
  `resources.arsc` and binary XML decoded. Messages, Contacts and Phone (AOSP forks, Views)
  and Calendar and Alarm (partly Views) keep their dimensions, styles and shapes there.
- **On-device measurements.** 36 screens across Messages, Contacts, the launcher, Settings
  (Mudita and Android), Calendar, Notes, Alarm, Meditation and Weather, measured from
  `uiautomator` bounds and pixel analysis. The Compose apps (Notes, Settings, launcher,
  Meditation, Weather) keep their values in code, so this is the only source for them.
  Notes and Alarm had no items, so only their empty states were seen. No centred dialog or
  date picker was reachable without creating data.
- Only layout was recorded. Screens were opened and backed out of; nothing was changed,
  except one accidental outgoing call to an SMS short code during the first capture pass,
  which was reported at once and led to the safety rules now in the skill's audit reference.

## What the stock apps are built with

- **Messages, Contacts, Phone**: Views, sharing a Mudita XML kit: `Mudita<Role><Size><Weight>`
  text styles on Lato Medium/Black, `KompaktButton.{Primary,Secondary}.{Large,Medium,Small}`,
  `line_dashed` dividers, `bottom_dialog_bg` sheets, a custom pill switch.
- **Calendar, Notes, Alarm, Settings, Meditation, Weather**: Compose, R8-minified. They ship
  byte-identical copies of MMD's Lato files, so they are most likely built on MMD (or an
  internal build of it), but with the overrides below.
- **Launcher**: Compose with its own selectable font sets.
- Colour: black and white everywhere, except a pink AOSP-leftover FAB in Contacts.

## Stock values vs MMD defaults vs our apps

| Token | Stock | MMD default | Hermes | Twofold | ntfy Kompakt | Verdict |
|---|---|---|---|---|---|---|
| Top bar | 64 dp + 3 dp black rule | 64 dp, 3 dp divider | 64 dp min + 3 dp | same | 64 dp + 3 dp | **Match** |
| Bar title | Lato **Black 25 sp**, at 16 dp | Medium 24 sp | Bold 24 sp, at 16 dp | Bold 24 sp | Bold 24 sp | Change to Black 25 sp |
| Label above title | not used in stock bars | none | small label over title | "Twofold · Best" label | none | Drop, or move into the title or first row |
| Bar icons | about 28 dp glyph, 48 dp target | 24 dp | 30 dp / 48 dp | 30 dp / 48 dp | toolbar default | Change to about 28 dp |
| Create "+" | black rounded rectangle 42 x 36 dp, 8 dp corners, 22 dp line "+" | none (FAB exists) | 48 dp square, 10 dp corners | n/a | 56 dp FAB, 12 dp corners | Change to 42 x 36, 8 dp |
| Row text inset | 16 dp (about 60 dp after an icon) | none | 20 dp | 12 dp | 20 dp | Change to 16 dp |
| Divider | **1 dp dashed, 2 dp / 2 dp**, from text start to right edge | solid 3 dp only | dashed 4/3, inset 20 dp | dashed 4/3, inset 20 dp | dashed 4/3, inset 20/8 dp | Change dash to 2/2, inset 16, no end inset |
| Row height | 64 dp single, 72 dp message list, 81 dp icon + 2 lines | none | about 70 dp (12 dp padding) | 1/7 of screen | about 70 dp | Set min heights |
| Row type | label 21 sp Medium; two-line: 23 sp Black over 21 sp Medium | body 20/18 Medium | title 20 Bold, preview 18 | 20 Bold / 15 | 20 Bold / 18 | Change to stock scale |
| Received bubble | 1 dp, 12 dp corners, square bottom-left | none | 1 dp, 14 dp, square bottom-left | n/a | 1 dp, 14 dp, square bottom-left | Change radius to 12 |
| Sent bubble | **2 dp**, 12 dp, square bottom-right | none | 2.5 dp, all round | n/a | n/a (all received) | Change to 2 dp with a tail |
| Selected bubble | 3.5 dp | none | n/a | n/a | 3 dp | Change to 3.5 dp |
| Message text | 18 sp Medium, padding 10/8 dp | | 20 sp, padding 12/8 | n/a | 20 sp | Change to 18 sp |
| Menus | **bottom sheet**: 3 dp top border, centred 25 sp Black title, stacked outlined 48 dp buttons, black Cancel; no popups found | dropdown, 20 dp corners, 2 dp outline | none | `DropdownMenuMMD` | outlined popup, 10 dp corners | Change to bottom sheet |
| Buttons | 48 dp (56 large, 36 small), 12 dp corners (about 10 in Compose apps, 8 small), 2 dp outline | min 32 dp, 8 dp, 2 dp | MMD defaults | MMD defaults | Material + 2 dp outline | Change to 48 dp, 12 dp |
| Switch | 48 x 30 dp pill | 52 x 32 dp | `SwitchMMD` (unused) | n/a | Material switch | Close enough in Compose; use the stock pill in XML |
| Radio | 26 dp | 20 dp | n/a | `RadioButtonMMD` | Material | Minor; keep MMD's |
| Checkbox | 20 dp | 23 dp | n/a | n/a | n/a | Minor |
| Selection | 20 dp checkbox per row, delete in the bar | none | n/a | n/a | 3 dp row outline, white action bar | Acceptable; checkbox is closer to stock |
| Settings | 64 dp rows, 21 sp Black headers, switch right, ">" for sub-pages, dashed dividers, radio sub-screens | none | form fields only | radio screen | 16 sp Bold headers, dashed inset dividers | Change header size and row height |
| Long lists | ▲/▼ buttons + 8 dp pill scrollbar | the same, in `LazyColumnMMD` | `LazyColumnMMD` default for sessions | n/a | RecyclerView, no buttons | **Match** in Compose; missing in ntfy |
| Long reading | follow-the-finger in conversations | `scrollStep = 0` | `scrollStep = 0`, no scrollbar | n/a | RecyclerView | Match |
| Empty state | illustration, 25 sp Black headline, 21 sp body, one outlined 48 dp button | none | status text only | n/a | ntfy default | Add |
| Centred dialog | not seen | n/a | n/a | 3 dp framed overlay | Material dialogs | Prefer a sheet; Twofold's in-game result overlay can stay |
| Animation, shadow, ripple | none | ripple off | none | none | none (ripple colours overridden) | **Match** |
| Colour | black and white | black and white | black and white | black and white, 30% disabled | black and white | **Match** |

## What each app should change

**Hermes** (`samples/hermes-kompakt`)

1. `MessagesTopBar`: title to 25 sp Black; drop the label row (put the profile in the title
   or a first line); icons to 28 dp.
2. `PrimaryBarIcon`: 42 x 36 dp black rectangle with 8 dp corners and a line "+".
3. `MessageBubble`: 12 dp corners; sent = 2 dp with a square bottom-right; 18 sp text with
   10/8 dp padding; 2 dp / 18 dp spacing for same / different author.
4. `DashedRowDivider`: 2/2 dp dash, 16 dp inset, to the right edge; session rows 72 dp,
   name 21 sp Black, preview and time 18 sp.
5. Settings: 64 dp rows, `KompaktButton`s at 48 dp, a bottom sheet for any future menu.
6. Theme with `KompaktTypography`.

**Twofold** (`samples/twofold`)

1. `KompaktTopBar`: 25 sp Black title (score), move "Best" out of the label into the title
   or the board area.
2. ⋮ menu: replace `DropdownMenuMMD` with a bottom sheet (New game, Tile style, Cancel).
3. Tile style rows: text at 16 dp, 2/2 dashed dividers, 21 sp labels.
4. Overlay buttons via `KompaktButton` (48 dp, 12 dp corners). Arrow pad outline 2 dp.

**ntfy Kompakt** (`zenibako/ntfy-android`, `kompakt-eink`)

1. Type scale to the Mudita kit sizes and Lato Black titles (add Lato Medium and Black).
2. `eink_row_divider` / `eink_preference_divider`: dash 2/2, inset 16 dp, no end inset.
3. FAB: black, 8 dp corners, or move "+" into the toolbar as a 42 x 36 dp button.
4. Message bubbles: 12 dp corners, selected 3.5 dp.
5. Overflow and per-message menus: a bottom sheet with stacked outlined buttons and a black
   Cancel, instead of the outlined popup.
6. Settings: 64 dp rows, 21 sp Black category titles, the stock pill switch.

## Open questions

- Centred dialogs and the date and time pickers were not reachable without creating data;
  check them next time there is an event or note to open.
- Lato Medium is not in MMD. Adding `lato_medium.ttf` to mmd-core would let `ThemeMMD`
  match the stock weight exactly.
- MMD's own defaults differ from Mudita's apps in the same ways listed above. A
  `KompaktTypography` and a dashed divider in mmd-core itself would make this the default.
