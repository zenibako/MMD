# Twofold

A sliding-tile number game (in the style of 2048) built with MMD for E Ink phones such as the
Mudita Kompakt.

Slide the board up, down, left or right. Equal tiles that touch merge into their sum. Reach the
2048 tile, then keep going if you like.

## Made for E Ink

- **No animation.** Each move is one redraw, so there is no ghosting from in-between frames.
- **Black-and-white tiles.** Values are told apart by line weight and fill, never colour. Five
  styles are available under **Style**, and the choice is remembered:
  - *Weight*: thin outline for 2 and 4, heavy outline for 8 to 64, solid black from 128, and an
    inner frame from 2048.
  - *Rounded*: the same, with MMD's 8 dp button and card corners.
  - *Frames*: one nested frame per doubling up to 16, then solid.
  - *Ink*: every tile solid black, with white inner frames from 128 and 2048.
  - *Numbers*: a plain grid where only the digits' weight changes.
- **Swipe or tap.** Swipe anywhere below the top bar, or use the four arrow buttons.
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
