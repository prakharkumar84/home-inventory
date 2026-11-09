# Home Inventory — offline-first Android app

Implementation of `offline_android_home_inventory_architecture.txt`.
Kotlin · Jetpack Compose (Material 3) · Room · CameraX · ML Kit on-device · no backend, no account, no API key.

## What is built

| Spec milestone | Status |
|---|---|
| M0 — project foundation (Kotlin, Compose, M3, navigation, Room, CameraX, Photo Picker) | Done |
| M1 — nested locations, manual items, search, breadcrumbs, move | Done |
| M2 — camera capture, gallery import, app-private photo store, thumbnails, review screen | Done |
| M3 — on-device labelling + OCR, suggestion mapping, uncertainty handling, unit tests | Done |
| M4 — multi-object detection (ML Kit detector wired; YOLO slot left modular) | Partial |
| M5 — MobileCLIP image-text matching | Interface only (`ImageTextMatcher`) |
| M6/M7 — export, QR labels, visual search, backup | Not started |

## Spec rules enforced in code

- **AI never writes to the inventory.** Suggestions live in `recognition_suggestion`; only user-confirmed rows become `inventory_item` records (`§9.4`, `§12.5`).
- **No fabricated certainty.** `SuggestionRanker` never averages scores across unrelated models. A single model can never produce a *Strong suggestion* — agreement between independent signals is required. Unclear results stay *Needs confirmation* (`§5.6`, `§5.7`).
- **AI failure never blocks the app.** Every pipeline stage is individually fault-tolerant; manual entry is always available (`§18`).
- **Moving never duplicates.** `move()` updates the record and writes an `item_movement` row (`§2.4`).
- **Deleting a location never orphans items.** Items are reassigned to *Unassigned* first; locations with sub-locations cannot be deleted until they are emptied (`§2.1`).
- **Models are swappable.** `ImageLabeller`, `ObjectDetector`, `TextReader`, `ImageTextMatcher` are interfaces; swapping ML Kit for a LiteRT/YOLO model touches no UI or database code (`§22.6`).
- **Privacy by construction.** No `INTERNET` permission is declared. Android Photo Picker instead of media permissions. Cloud backup excluded in `data_extraction_rules.xml` (`§15`).

## Offline behaviour — be precise about this

Image labelling and text recognition use the **bundled** ML Kit artifacts, so they work with no network at all. Object detection ships as an **unbundled** library, so Google Play services downloads that model once; afterwards it runs on device. If it is unavailable, the pipeline falls back to whole-image labels and manual entry.

## Build

1. Open the folder in Android Studio (Hedgehog or newer) and let it generate the Gradle wrapper, or run `gradle wrapper` once.
2. `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
3. `./gradlew test` runs the ranker/normalisation unit tests.

`minSdk 24`, `compileSdk 34`. ML Kit requires API 23+.

## Open decisions from §22

Defaults chosen, all still configurable:

| Question | Default implemented |
|---|---|
| Storage & backup | Phone-only. Export/restore not built yet; backup adapter kept out of core logic. |
| Recognition interaction | Both — automatic multi-object detection plus manual "Add row". |
| Inventory scope | Multiple top-level spaces supported in the data model from day one. |
| Min Android version | 24 |
| Distribution | Sideloadable debug APK; nothing Play-specific assumed. |

## Next steps

1. Export/import (`§11`, M6) — JSON + photo folder, since the schema is already path-based.
2. FTS5 and search aliases (`§10`) — `normalizedName` is already stored for this.
3. QR location labels (`§14`) — encode only the opaque location id.
4. Swap in a mobile YOLO model behind `ObjectDetector`, verifying weights licence before bundling.
