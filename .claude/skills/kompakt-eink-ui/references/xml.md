# Kompakt E Ink look for Android XML views

Read `SKILL.md` first for the rules and stock numbers. This reference is how to get
there in a classic View app with as little Kotlin as possible. It comes from the ntfy
Kompakt fork (`zenibako/ntfy-android`, branch `kompakt-eink`), corrected against the stock
Messages, Contacts and Phone apps, which are View apps themselves and share a Mudita XML
kit (`MuditaTitle*`/`MuditaBody*`/`MuditaLabel*` text styles, `KompaktButton.*` buttons,
`line_dashed` dividers, `bottom_dialog_bg` sheets). Where this file gives a resource, it
mirrors that kit.

## Strategy: a flavor that overrides resources by name

1. Add a product flavor (e.g. `kompakt`) with `applicationIdSuffix ".kompakt"` and
   `buildConfigField 'boolean', 'E_INK', 'true'` (false in the other flavors).
2. Put the look in `src/kompakt/res/`. Resources there replace `main`'s of the same name, so
   override `AppTheme`, colours, dimens, drawables, menus and whole layouts by name while
   keeping every view ID.
3. Touch Kotlin only where the app computes colours or behaviour at runtime (status bar and
   toolbar colours, day markers in an adapter, swapping a popup for a sheet), guarded by
   `BuildConfig.E_INK`.
4. Copy the `values/` theme into `values-night/` too, so the dark setting changes nothing.

## Fonts

`res/font/`: Lato Medium (500) and Lato Black (900), plus Regular and Bold if the app needs
them. MMD ships Black, Bold and Regular (`mmd-core/src/androidMain/res/font`); take Lato
Medium from the Lato release (OFL); if only MMD's files are at hand, its `lato_semi_bold.ttf` is closer to the stock weight than Regular. `res/font/lato.xml` is a `<font-family>` with each file
at its weight (`android:fontWeight` and `app:fontWeight` both set).

## Theme (`values/themes.xml`)

Parent `Theme.Material3.Light.NoActionBar`. Map every `color*` attribute to black or white
(primary / onSurface / outline = black; surface / background / containers = white; inverse
roles swap). Then:

```xml
<item name="fontFamily">@font/lato</item>
<item name="android:fontFamily">@font/lato</item>
<!-- Stock Mudita kit scale (Medium 500 / Black 900) -->
<item name="textAppearanceHeadlineLarge">@style/TextAppearance.EInk.HeadlineLarge</item> <!-- 36sp Black -->
<item name="textAppearanceTitleLarge">@style/TextAppearance.EInk.TitleLarge</item>       <!-- 25sp Black: bar, sheet title -->
<item name="textAppearanceTitleMedium">@style/TextAppearance.EInk.TitleMedium</item>     <!-- 23sp Black: two-line row title -->
<item name="textAppearanceTitleSmall">@style/TextAppearance.EInk.TitleSmall</item>       <!-- 21sp Black: section header, list name -->
<item name="textAppearanceBodyLarge">@style/TextAppearance.EInk.BodyLarge</item>         <!-- 21sp Medium: row label, body -->
<item name="textAppearanceBodyMedium">@style/TextAppearance.EInk.BodyMedium</item>       <!-- 18sp Medium: messages, times -->
<item name="textAppearanceBodySmall">@style/TextAppearance.EInk.BodySmall</item>         <!-- 15sp Medium -->
<item name="textAppearanceLabelLarge">@style/TextAppearance.EInk.LabelLarge</item>       <!-- 21sp Black: buttons -->
<item name="textAppearanceLabelMedium">@style/TextAppearance.EInk.LabelMedium</item>     <!-- 18sp Black -->
<item name="textAppearanceLabelSmall">@style/TextAppearance.EInk.LabelSmall</item>       <!-- 15sp Medium -->

<!-- No ripples, animations or shadows -->
<item name="colorControlHighlight">@android:color/transparent</item>
<item name="android:colorControlHighlight">@android:color/transparent</item>
<item name="android:windowAnimationStyle">@null</item>
<item name="android:windowContentTransitions">false</item>
<item name="android:windowActivityTransitions">false</item>
<item name="elevationOverlayEnabled">false</item>

<!-- White system bars with dark icons -->
<item name="android:statusBarColor">@android:color/white</item>
<item name="android:navigationBarColor">@android:color/white</item>
<item name="android:windowLightStatusBar">true</item>
<item name="android:windowLightNavigationBar">true</item>

<item name="materialCardViewStyle">@style/CardView</item>             <!-- outlined, 0 elevation -->
<item name="toolbarStyle">@style/EInkToolbar</item>                   <!-- TitleLarge (25sp Black) -->
<item name="actionModeStyle">@style/ActionMode</item>                 <!-- white + 3dp rule -->
<item name="materialButtonStyle">@style/KompaktButton.Primary</item>
<item name="materialButtonOutlinedStyle">@style/KompaktButton.Secondary</item>
<item name="bottomSheetDialogTheme">@style/KompaktSheetDialog</item>
<item name="preferenceTheme">@style/EInkPreferenceTheme</item>
```

Each `TextAppearance.EInk.*` sets `android:textSize`, `android:lineHeight` (about 1.2x),
`android:textFontWeight` (500 or 900), `fontFamily`/`android:fontFamily` = `@font/lato`, and
`android:textColor` = black.

Ripples are colour resources too: override Material's `m3_*_ripple_color*` and
`mtrl_*_ripple_color` selectors in `src/kompakt/res/color/` with transparent for pressed and
a static `#52000000` for focused, so keyboard focus stays visible without animation.

## Resources to add

`values/dimens.xml`

```xml
<dimen name="eink_bar_height">64dp</dimen>
<dimen name="eink_bar_rule">3dp</dimen>
<dimen name="eink_text_inset">16dp</dimen>
<dimen name="eink_icon_text_inset">60dp</dimen>
<dimen name="eink_row_height">64dp</dimen>
<dimen name="eink_conversation_row_height">72dp</dimen>
<dimen name="eink_button_height">48dp</dimen>
<dimen name="detail_card_elevation">0dp</dimen>
```

`drawable/line_dashed.xml` (the stock row divider)

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="line">
    <stroke android:width="1dp" android:color="@android:color/black"
        android:dashWidth="2dp" android:dashGap="2dp" />
</shape>
```

Show it on a 2dp-tall `View`/`ImageView` with `android:layout_marginStart="@dimen/eink_text_inset"`
(or `eink_icon_text_inset` after an icon), running to the right edge, with
**`android:layerType="software"`**: hardware rendering draws dashed lines solid. For a row
background with a dashed bottom edge, use the stock `bg_dashed` layer-list: a transparent
rectangle plus `line_dashed` at `gravity="bottom"`, `height="2dp"`.

`drawable/eink_preference_divider.xml`: `line_dashed` inside an `<inset>` with
`insetLeft="@dimen/eink_text_inset"` and `<size android:height="2dp"/>`.

`drawable/toolbar_background.xml` (white bar with a 3dp black bottom edge, as stock)

```xml
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item><shape><solid android:color="@android:color/black" /></shape></item>
    <item android:bottom="@dimen/eink_bar_rule"><shape><solid android:color="@android:color/white" /></shape></item>
</layer-list>
```

Use the same shape as the `ActionMode` style's `background` for the selection bar.

Buttons (`KompaktButton` styles, stock values):

```xml
<!-- button_primary_shape: 12dp corners, solid black. button_secondary_shape: 12dp, 2dp black stroke.
     *_small variants: 8dp corners. -->
<style name="KompaktButton.Primary" parent="Widget.Material3.Button">
    <item name="backgroundTint">@android:color/black</item>
    <item name="android:textColor">@android:color/white</item>
    <item name="cornerRadius">12dp</item>
    <item name="android:minHeight">@dimen/eink_button_height</item>
    <item name="android:insetTop">0dp</item>
    <item name="android:insetBottom">0dp</item>
    <item name="android:textAppearance">@style/TextAppearance.EInk.LabelLarge</item>
    <item name="rippleColor">@android:color/transparent</item>
    <item name="elevation">0dp</item>
</style>
<style name="KompaktButton.Secondary" parent="Widget.Material3.Button.OutlinedButton">
    <item name="strokeColor">@android:color/black</item>
    <item name="strokeWidth">2dp</item>
    <item name="android:textColor">@android:color/black</item>
    <item name="cornerRadius">12dp</item>
    <item name="android:minHeight">@dimen/eink_button_height</item>
    <item name="android:insetTop">0dp</item>
    <item name="android:insetBottom">0dp</item>
    <item name="android:textAppearance">@style/TextAppearance.EInk.LabelLarge</item>
    <item name="rippleColor">@android:color/transparent</item>
</style>
```

Message bubbles: one shape per variant, white fill, black stroke, 12dp corners with one
square tail corner. Use them as the bubble's background (or a CardView foreground) and pick
by direction and `state_activated`:

```xml
<!-- incoming_msg_bubble_bg: 1dp, bottom-left square -->
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@android:color/white" />
    <stroke android:width="1dp" android:color="@android:color/black" />
    <corners android:topLeftRadius="12dp" android:topRightRadius="12dp"
        android:bottomRightRadius="12dp" android:bottomLeftRadius="0dp" />
</shape>
<!-- outgoing_msg_bubble_bg: 2dp stroke, bottom-right square.
     *_selected_*: the same shapes with a 3.5dp stroke. -->
```

Text inside: 18sp Medium, 10dp horizontal and 8dp vertical padding; 2dp between messages
from the same author, 18dp between authors; time 4dp under the bubble.

Bottom sheet (`drawable/bottom_dialog_bg.xml`, stock): white with only a 3dp black top
border, no radius.

```xml
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item><shape><solid android:color="@android:color/white" />
        <stroke android:width="3dp" android:color="@android:color/black" /></shape></item>
    <item android:top="3dp"><shape><solid android:color="@android:color/white" /></shape></item>
</layer-list>
```

`KompaktSheetDialog` (parent `Theme.Material3.Light.BottomSheetDialog`): window background
transparent, `android:backgroundDimEnabled` false, `android:windowAnimationStyle` @null, and
a `bottomSheetStyle` whose `backgroundTint` is null, `android:background` is
`bottom_dialog_bg`, `shapeAppearance` has 0 corners, and `elevation` is 0. Lay the sheet out
as a centred TitleLarge title, then full-width `KompaktButton.Secondary` actions (12dp side
margins, 16dp gaps) and a final `KompaktButton.Primary` "Cancel". Set `behavior.state = STATE_EXPANDED`
in `onCreateDialog` (or `onStart`), before the sheet is first laid out, so it is placed
expanded instead of settling there; changing the state after layout animates.
`skipCollapsed = true` only makes hiding skip the collapsed state, so set both. With
`android:windowAnimationStyle` @null on the dialog it then appears in one redraw.

Switch (stock `kompakt_switch_selector`, used as a CheckBox's `android:button`): a 48 x 30dp
vector pill. Off: white fill, 1.5dp black outline, black knob on the left. On: black fill,
white knob on the right. Use it for `SwitchPreferenceCompat`'s widget layout too.

## Layouts

**Top bar**: a white `AppBarLayout` (`app:elevation="0dp"`, `app:liftOnScroll="false"`,
`android:stateListAnimator="@null"`) containing a `MaterialToolbar` of
`@dimen/eink_bar_height`, then a 3dp black `View` (or one 67dp toolbar with
`toolbar_background`). Title 25sp Black at 16dp (`app:titleMarginStart` / content inset).
Icons black, 48dp targets with 10dp padding. If the app computes toolbar colours in code,
return black-on-white when `E_INK`.

**Create action**: stock has no FAB. Put the action in the toolbar as an `ImageButton`
42 x 36dp, background `button_primary_shape_small` (8dp corners, black), a white 22dp "+"
with 2dp strokes, 16dp from the right edge. If moving it is too invasive, restyle the FAB:
black, 0 elevation, transparent ripple, a rounded-rectangle `shapeAppearanceOverlay` (8dp).

**List row**: `minHeight` 64dp (72dp for message lists, 81dp with an icon and two lines),
text at 16dp, 10dp vertical padding. Single line: BodyLarge (21sp Medium). Two lines:
TitleMedium (23sp Black) over BodyLarge. Message list: TitleSmall name with BodyMedium time
baseline-aligned at the right, one BodyMedium preview line. `line_dashed` divider at the
bottom from the text start. Hide decorative icons (`gone`) but keep their IDs.

**Selection mode**: stock Messages shows a 20dp checkbox at the start of each row and a
delete action in the bar. If the app already marks selected rows another way, use a 3dp
black outline as `android:foreground` on `state_activated`, never a grey fill.

**Menus**: stock apps have no popup menus. Replace the ⋮ `PopupMenu`/options menu with the
bottom sheet above where the code allows. If it must stay a popup, at least make it flat:
`android:popupBackground` white with a 2dp black outline, `popupElevation` 0, and
`popupEnterTransition`/`popupExitTransition`/`popupAnimationStyle` @null.

**Settings** (AndroidX Preference): `EInkPreferenceTheme` sets
`preferenceCategoryTitleTextAppearance` to TitleSmall (21sp Black, black), and every
preference style (`preferenceStyle`, `preferenceScreenStyle`, `dialogPreferenceStyle`,
`editTextPreferenceStyle`, and `switchPreferenceCompatStyle` on the app theme) to a style
with `iconSpaceReserved=false`, `allowDividerAbove=true`, `allowDividerBelow=true`, and a
layout of 64dp minimum height with the title in BodyLarge (or TitleMedium over a BodyLarge
summary). `EInkPreferenceFragment` sets `android:divider` to the inset dashed divider,
`android:dividerHeight` 2dp and `allowDividerAfterLastItem=false`. List preferences open a
sub-screen or sheet of 62dp radio rows rather than an AlertDialog where possible.

**Full-screen alert**: white `LinearLayout`, 24dp padding, `fitsSystemWindows`; an ALL CAPS
LabelMedium (18sp Black) label; a HeadlineLarge (36sp Black) title, `maxLines="3"`; a
`ScrollView` (`scrollbars="none"`, `overScrollMode="never"`) with BodyLarge text; then a
56dp full-width `KompaktButton.Primary` "Open" and `KompaktButton.Secondary` "Dismiss".
Window background white, `windowShowWallpaper` false.

## Review

Run the checklist in `SKILL.md`. Then grep `src/<flavor>/res` and the layouts it
inherits from `main` for `elevation`, `cardElevation`, `rippleColor`,
`?attr/selectableItemBackground` (a ripple), `liftOnScroll`, `animateLayoutChanges`,
`PopupMenu`, `AlertDialog`, `@color/` values that are not black, white or transparent, and
`ic_*_gray*` drawables without a black tint. Check `values-night` matches `values`. Build the
flavor and look at it on the Kompakt itself.
