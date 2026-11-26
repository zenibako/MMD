# Kompakt E Ink look in CSS and WebView apps

Read `SKILL.md` first: it holds the rules and the stock numbers. This reference is how to
meet them in CSS, for an app the Kompakt shows in a WebView: an Obsidian theme (where these
notes come from, tested on the phone in November 2026), a web page, or a Capacitor or
Electron-style app.

## Units

- On the Kompakt, **1 CSS px = 1 dp** (`devicePixelRatio` is 1.333), so the stock numbers
  carry over: a 64 dp bar is `64px`, 21 sp row text is `21px`.
- The screen is 360 x 600 px, and the app gets about **360 x 564 px** under the 36 px status
  bar. Test at 360 x 564.
- This holds only with a device-width viewport: the page needs
  `<meta name="viewport" content="width=device-width, initial-scale=1">`, and a WebView you
  control should not force a wide viewport (`setUseWideViewPort(true)` without that meta lays
  out at 980 px). Check `window.innerWidth` is 360 on the phone before trusting a width-based
  media query.
- **Borders round down.** Chromium snaps border widths to whole device pixels and floors
  them, so `3px` (3.999 device px) draws as 3 device px (2.25 dp) and `2px` as 2 (1.5 dp).
  On the phone use **`3.1px`** for 3 dp rules and **`2.3px`** for 2 dp outlines, which land on
  4 and 3 device px. 1 dp hairlines can stay `1px`.

## Scope the phone sizes

Keep desktop and phone in one stylesheet and put the Kompakt sizes under a phone class
(`.is-phone` in Obsidian, or a media query such as `(max-width: 400px)`). Desktop can keep
denser type and hover states; on the phone set hover backgrounds to `transparent`, since a
touch leaves the last hover painted on E Ink.

## Fonts

- Android has no Lato and an app can't install fonts, so embed it: `@font-face` with base64
  `src` (or files next to the CSS) under **your own family name** ("MMD Lato"), and apply it
  through the theme's own font variables. `@font-face` makes the family available to the
  page only, not to the system: an app's font picker may report it as missing (Obsidian
  shows plain "Lato" as not detected) even though the page renders with it.
- Weights: 400, 700, 900 and their italics from MMD's `lato_*.ttf`, plus **500 from
  `lato_semi_bold.ttf`** for row and body text. Regular 400 reads visibly lighter than the
  stock apps on the panel. Subset to Latin with `pyftsubset` to keep the size down (MMD's
  SemiBold file alone is 669 KB).
- If the app syncs its settings to other devices, a device without the theme falls back to
  its default font. Prefer setting the font through the theme's variables over a per-app
  font setting.

## Mapping the rules

| Rule | CSS |
|---|---|
| Black on white only | Every colour variable to `#000`/`#fff`, in light and dark themes alike; the one grey is `rgba(0,0,0,.3)` for placeholders and disabled glyphs |
| No motion | `*, *::before, *::after { transition: none !important; animation-duration: 0s !important; animation-delay: 0s !important; }`, plus the app's own duration variables to 0, which stops sliding drawers. The text caret's blink is separate: add `caret-animation: manual` (Chromium 139+; the Kompakt's WebView was 149 in November 2026). Older WebViews keep blinking; accept it or use `caret-color: transparent` only where a visible caret isn't needed |
| Flat | `box-shadow: none`, no backdrop or scrim behind menus and modals |
| Top bar | 64 px tall, 25 px weight 900 title, `border-bottom: 3.1px solid #000` on the phone; icons 24 to 28 px in 48 px targets |
| Dashed divider | `background: linear-gradient(to right, #000 50%, transparent 50%) left bottom / 4px 1px repeat-x` on the row, which gives exact 2/2 dashes (`border-style: dashed` doesn't) |
| Rows | 64 px settings rows, 21 px weight 500 labels, 18 px descriptions in black; section headers 21 px weight 900 |
| Dense trees | A file tree may keep 48 px rows so more of it fits; say so in the theme's notes |
| Current item | Inverse (black fill, white text) or 3 dp outline, never a grey or tinted fill |
| Bottom sheets | The app's phone menus become sheets: `border-top: 3.1px solid #000`, no radius; items are 48 px outlined buttons (2.3px, 12 px corners) with centred 21 px weight 900 labels and no icons; a centred 25 px weight 900 title; any Cancel filled black |
| Buttons and fields | 48 px tall, 2.3px outline, 12 px corners (fields 10 px), 21 px text; the primary button filled black |
| Switch | 48 x 30 pill: off = white with a black outline and black knob on the left, on = black with a white knob on the right |
| Scrollbar | A thin black pill (6 to 8 px, rounded). A WebView can't add the ▲/▼ buttons, so don't fake them |
| Icons | Solid black, never 30% (chevrons, handles, counts). Hide decorative icons in sheets |
| Highlights, callouts, code | Outlined, no tint; text highlight as inverse |

## Working on the phone

- Find real class names with the WebView's remote devtools (`chrome://inspect` over adb)
  before writing selectors; class names remembered from docs are often wrong.
- Reload the stylesheet in place rather than restarting the app; in Obsidian,
  `app.customCss.requestLoadTheme()` from devtools.
- Keep fixes found on the device in one marked block at the end of the file
  (`/* === Kompakt fixes rN === */`), back up the file before each round, and fold the
  blocks into the source afterwards.
- Follow [audit.md](audit.md) for driving the phone: one input per step, screenshot first,
  open menus but never pick an action, open no personal notes or documents.

## Review

Run the checklist in `SKILL.md`, then grep the CSS for `transition`, `animation`,
`box-shadow`, `rgba(` and `#` colours other than black, white and the 30% faint, `3px` or
`2px` borders inside the phone scope, and hover rules that apply on the phone.
