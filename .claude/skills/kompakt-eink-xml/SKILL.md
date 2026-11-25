---
name: kompakt-eink-xml
description: Apply the Kompakt/MMD E Ink look to Android XML-view apps (Material Components, AppCompat, RecyclerView, Preferences), usually as a build flavor of an existing app. Use when theming or reviewing a View-based app for the Mudita Kompakt.
---

# Kompakt E Ink look for Android XML views

Read `kompakt-eink-design` first for the rules and numbers. This skill is how to get there in
a classic View app without touching its Kotlin much. It is distilled from the ntfy Kompakt
fork (`zenibako/ntfy-android`, branch `kompakt-eink`), which restyled a Material 3 XML app
to match the Kompakt's Messages app.

## Strategy: a flavor that overrides resources by name

1. Add a product flavor (e.g. `kompakt`) with `applicationIdSuffix ".kompakt"` and
   `buildConfigField 'boolean', 'E_INK', 'true'` (false in the other flavors).
2. Put the look in `src/kompakt/res/`. Resources there replace `main`'s of the same name, so
   override `AppTheme`, colours, dimens, drawables, menus and whole layouts by name while
   keeping every view ID. Main's code keeps working.
3. Touch Kotlin only where the app computes colours or behaviour at runtime (status bar
   colour, toolbar text colour, day markers in an adapter), guarded by `BuildConfig.E_INK`.
4. Copy `values/` overrides into `values-night/` too, so the system dark setting changes
   nothing (E Ink is light only).

## Theme (`values/themes.xml`)

Parent `Theme.Material3.Light.NoActionBar`. Map every `color*` attribute to black or white
(MMD's `eInkColorScheme`: primary / onSurface / outline = black; surface / background /
containers = white; inverse roles swap). Then add:

```xml
<!-- Lato everywhere, MMD type scale -->
<item name="fontFamily">@font/lato</item>
<item name="android:fontFamily">@font/lato</item>
<item name="textAppearanceHeadlineLarge">@style/TextAppearance.EInk.HeadlineLarge</item> <!-- 28sp -->
<item name="textAppearanceTitleLarge">@style/TextAppearance.EInk.TitleLarge</item>       <!-- 24sp -->
<item name="textAppearanceTitleMedium">@style/TextAppearance.EInk.TitleMedium</item>     <!-- 20sp -->
<item name="textAppearanceTitleSmall">@style/TextAppearance.EInk.TitleSmall</item>       <!-- 16sp -->
<item name="textAppearanceBodyLarge">@style/TextAppearance.EInk.BodyLarge</item>         <!-- 20sp -->
<item name="textAppearanceBodyMedium">@style/TextAppearance.EInk.BodyMedium</item>       <!-- 18sp -->
<item name="textAppearanceBodySmall">@style/TextAppearance.EInk.BodySmall</item>         <!-- 15sp -->
<item name="textAppearanceLabelLarge">@style/TextAppearance.EInk.LabelLarge</item>       <!-- 18sp -->
<item name="textAppearanceLabelMedium">@style/TextAppearance.EInk.LabelMedium</item>     <!-- 15sp -->
<item name="textAppearanceLabelSmall">@style/TextAppearance.EInk.LabelSmall</item>       <!-- 14sp -->

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

<item name="materialDividerStyle">@style/EInkDivider</item>          <!-- 1dp black -->
<item name="materialCardViewStyle">@style/CardView</item>            <!-- outlined 1dp, 0 elevation -->
<item name="toolbarStyle">@style/EInkToolbar</item>                  <!-- bold TitleLarge -->
<item name="actionModeStyle">@style/ActionMode</item>                <!-- white + 3dp rule -->
<item name="popupMenuStyle">@style/EInkPopupMenu</item>
<item name="android:popupMenuStyle">@style/EInkPopupMenu</item>
<item name="actionOverflowMenuStyle">@style/EInkPopupMenu.Overflow</item>
<item name="android:actionOverflowMenuStyle">@style/EInkPopupMenu.Overflow</item>
<item name="listPopupWindowStyle">@style/EInkPopupMenu.ListPopupWindow</item>
<item name="android:listPopupWindowStyle">@style/EInkPopupMenu.ListPopupWindow</item>
<item name="textAppearanceLargePopupMenu">@style/TextAppearance.EInk.BodyLarge</item>
<item name="textAppearanceSmallPopupMenu">@style/TextAppearance.EInk.BodyLarge</item>
<item name="preferenceTheme">@style/EInkPreferenceTheme</item>
```

Each `TextAppearance.EInk.*` style sets `android:textSize`, `fontFamily` +
`android:fontFamily` = `@font/lato`, and `android:textColor` = `?attr/colorOnSurface`.
`res/font/lato.xml` is a font family of Lato Regular (400), Italic and Bold (700), copied
from MMD (`mmd-core/src/androidMain/res/font`).

Material's ripple colours are resources too: override the `m3_*_ripple_color*`,
`mtrl_*_ripple_color` colour selectors in `src/kompakt/res/color/` with transparent for
pressed and a static `#52000000` for focused, so keyboard focus stays visible without a ripple.

## Resources to add

`values/dimens.xml`

```xml
<dimen name="eink_bar_rule">3dp</dimen>
<dimen name="eink_row_inset_start">20dp</dimen>
<dimen name="eink_row_inset_end">8dp</dimen>
<dimen name="detail_card_elevation">0dp</dimen>
```

`drawable/eink_row_divider.xml` (dashed row divider)

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="line">
    <stroke android:width="1dp" android:color="@android:color/black"
        android:dashWidth="4dp" android:dashGap="3dp" />
</shape>
```

Use it on a 2dp-tall `View` with **`android:layerType="software"`**: hardware rendering draws
dashed line shapes solid.

`drawable/eink_preference_divider.xml`: the same line inside an `<inset>` with
`insetLeft="@dimen/eink_row_inset_start"`, `insetRight="@dimen/eink_row_inset_end"` and
`<size android:height="2dp"/>`.

`drawable/eink_row_foreground.xml` (selection = outline, not fill)

```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_activated="true">
        <shape android:shape="rectangle">
            <solid android:color="@android:color/transparent" />
            <stroke android:width="3dp" android:color="@android:color/black" />
        </shape>
    </item>
    <item android:drawable="@android:color/transparent" />
</selector>
```

`drawable/eink_bubble.xml` (received message) and `eink_bubble_selected.xml` (3dp stroke),
combined in a selector on `state_activated` and set as a CardView `android:foreground`:

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@android:color/transparent" />
    <stroke android:width="1dp" android:color="@android:color/black" />
    <corners android:topLeftRadius="14dp" android:topRightRadius="14dp"
        android:bottomRightRadius="14dp" android:bottomLeftRadius="0dp" />
</shape>
```

`drawable/eink_popup_background.xml` (menus)

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@android:color/white" />
    <stroke android:width="1.5dp" android:color="@android:color/black" />
    <corners android:radius="10dp" />
    <padding android:top="4dp" android:bottom="4dp" />
</shape>
```

Popup styles set `android:popupBackground` to it, `android:popupElevation` 0dp, and
`android:popupEnterTransition`, `android:popupExitTransition`, `android:popupAnimationStyle`
to `@null`.

`drawable/eink_action_mode_background.xml` (selection bar)

```xml
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@android:color/white" />
    <item android:gravity="bottom" android:height="@dimen/eink_bar_rule"
        android:drawable="@android:color/black" />
</layer-list>
```

with an `ActionMode` style using it as `background`, a bold black title, a black close
button tint, and selection menus that set `app:iconTint="@android:color/black"`.

## Layouts

**Top bar** (`app_bar_*.xml`): a white `AppBarLayout` with `app:elevation="0dp"`,
`app:liftOnScroll="false"`, `android:stateListAnimator="@null"`; a 64dp `MaterialToolbar`;
then a `View` of height `@dimen/eink_bar_rule` with a black background. The toolbar title is
bold TitleLarge via `EInkToolbar`. Keep the toolbar text and icons black: if the app
computes toolbar colours in code, return `colorOnSurface` / `colorSurface` when `E_INK`.

**FAB**: the main create action is a filled black rounded square, not a circle:

```xml
<style name="FloatingActionButton" parent="@style/Widget.Material3.FloatingActionButton.Primary">
    <item name="backgroundTint">@android:color/black</item>
    <item name="tint">@android:color/white</item>
    <item name="elevation">0dp</item>
    <item name="pressedTranslationZ">0dp</item>
    <item name="hoveredFocusedTranslationZ">0dp</item>
    <item name="rippleColor">@android:color/transparent</item>
    <item name="shapeAppearanceOverlay">@style/EInkFabShape</item> <!-- rounded, 10dp -->
</style>
```

If the app has room in its toolbar, prefer moving the action there as a 48dp black square
(see the Compose skill's `PrimaryBarIcon`).

**List row** (`ConstraintLayout`): `paddingStart="@dimen/eink_row_inset_start"`,
`paddingEnd="@dimen/eink_row_inset_end"`, `android:foreground="@drawable/eink_row_foreground"`;
title `TextView` with `textAppearanceTitleMedium`, `textStyle="bold"`, `maxLines="1"`,
`ellipsize="end"`, 12dp top margin; time `TextView` with `textAppearanceBodySmall`,
baseline-aligned to the title, 12dp start margin, end of parent; second line with
`textAppearanceBodyMedium`, one line, 2dp top and 12dp bottom margin; the software-layer
dashed divider `View` at the bottom. Hide decorative icons (`visibility="gone"`) but keep
their IDs. Tint any remaining status icons black, 20dp.

**Message**: a vertical `LinearLayout` (16dp horizontal and 6dp vertical padding) holding a
day-marker `TextView` (BodyMedium, centred, `gone` unless first of its day), the CardView
with `cardCornerRadius="0dp"`, `cardElevation="0dp"` and the bubble foreground, and the time
under it in BodySmall. Compute the day marker in the adapter (`"• 23 Nov •"`) and rebind
all items on list change, since DiffUtil does not rebind neighbours.

**Settings** (AndroidX Preference): `EInkPreferenceTheme` sets
`preferenceCategoryTitleTextAppearance` to bold black TitleSmall, and every preference style
(`preferenceStyle`, `preferenceScreenStyle`, `dialogPreferenceStyle`,
`editTextPreferenceStyle`, and `switchPreferenceCompatStyle` on the app theme itself) to a
style with `iconSpaceReserved=false`, `allowDividerAbove=true`, `allowDividerBelow=true`.
Categories set both to false. `EInkPreferenceFragment` sets `android:divider` to the inset
dashed divider, `android:dividerHeight` 2dp and `allowDividerAfterLastItem=false`.

**Full-screen alert** (an Activity launched by a full-screen intent): white
`LinearLayout`, 24dp padding, `fitsSystemWindows`; ALL CAPS LabelLarge label; bold
HeadlineLarge title (`maxLines="3"`); a `ScrollView` (`scrollbars="none"`,
`overScrollMode="never"`) with BodyLarge text; a full-width black `MaterialButton` (56dp
min height, transparent ripple) and an outlined one (2dp black stroke). Theme it with
`windowBackground` white and `windowShowWallpaper` false.

**Dialogs**: give full-screen dialog themes `windowAnimationStyle` with null enter and exit
animations. Alert dialogs: white, 0 elevation, black outlined buttons.

## Review

Run the checklist in `kompakt-eink-design`. Then grep `src/<flavor>/res` and the layouts it
inherits from `main` for `elevation`, `cardElevation`, `rippleColor`, `?attr/selectableItemBackground`
(a ripple), `liftOnScroll`, `android:animateLayoutChanges`, `@color/` entries that are not
black, white or transparent, and `ic_*_gray*` drawables without a black tint. Check
`values-night` matches `values`. Build the flavor and look at it on the Kompakt itself.
