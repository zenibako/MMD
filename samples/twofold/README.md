# Twofold

A sliding-tile number game (in the style of 2048) built with MMD for E Ink phones such as the
Mudita Kompakt.

Slide the board up, down, left or right. Equal tiles that touch merge into their sum. Reach the
2048 tile, then keep going if you like.

## Made for E Ink

- **No animation.** Each move is one redraw, so there is no ghosting from in-between frames.
- **Black-and-white tiles.** Values are told apart by line weight and fill, never colour. Four
  styles are available under **⋮ → Tile style**, and the choice is remembered:
  - *Weight*: thin outline for 2 and 4, heavy outline for 8 to 64, solid black from 128 with a
    white dot for each doubling after that, and an inner frame from 2048.
  - *Frames*: one nested frame per doubling up to 16, then solid, with a white dot per doubling
    from 64.
  - *Fold*: a black corner folds in further each doubling; from 128 the tile turns black and a
    white corner grows.
  - *Steps*: one dot along the bottom of the tile per doubling, wrapping onto a second row past
    2048.
- **Swipe or tap.** Swipe anywhere below the top bar, or use the four arrow buttons.
- **Kompakt look.** The UI follows the repo's `kompakt-eink-ui` skill, taken from the stock
  apps: Lato Medium/Black type scale, a 64 dp top bar with a 25 sp Black title and a 3 dp
  rule, Undo on the bar and the rest in a ⋮ bottom sheet (New game, Tile style, Cancel), 48 dp
  buttons with 12 dp corners, 2/2 dashed dividers, and outlined arrow buttons with MMD's
  chevrons. Tile style is a radio sub-screen with a back arrow.
- **Undo.** One step back, including after **New**, so an accidental tap never loses a game.
- **Offline.** No permissions. The game is saved after every move.

## Build and install

```sh
./gradlew :samples:twofold:assembleDebug
adb install -r samples/twofold/build/outputs/apk/debug/twofold-debug.apk
./gradlew :samples:twofold:testDebugUnitTest   # game logic tests
```

## Credits

The slide-and-merge logic in `game/Engine.kt` is adapted from
[android-2048-compose](https://github.com/alexjlockwood/android-2048-compose) by Alex Lockwood,
MIT License; the licence text is kept in that file's header. The game concept is Gabriele
Cirulli's 2048. The UI, persistence, undo and E Ink styling are new for this sample.
