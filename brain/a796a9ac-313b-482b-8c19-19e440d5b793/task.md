# PS5 / Steam Deck Style Frontend UI Redesign

## Planning
- [x] Review current implementation of `LibraryFrontendPane`, `LibraryScreen`, and `LibraryOptionsPanel`.
- [x] Formulate design for the new UI and interactions.
- [x] Create implementation plan.

## Execution
- [x] Implement AIO Store Toggle in Filters / Layout.
- [x] Lock orientation to Landscape when Frontend layout is selected.
- [x] Redesign `LibraryFrontendPane.kt` to match PS5 / Steam Deck style.
  - [x] Top tabs (Library, Store, Downloads OR Library, Steam, Epic, GOG, Amazon, Downloads depending on AIO toggle).
  - [x] Horizontal scroll for Library with 4.5 games visible, highlighting selected active game.
  - [x] Store format: 4 games wide, vertical scroll.
- [x] Implement Controller Support.
  - [x] L1/R1 to switch between top tabs.
  - [x] D-Pad / Thumbstick navigation mapped for all on-screen elements.
- [x] Ensure Modern styling, smooth animations, and optimized performance.
- [x] Build and verify with `assembleDebug`.

## Verification
- [ ] Test UI looks and feels like a PS5 / Steam Deck.
- [ ] Test layout toggles and landscape locking.
- [ ] Test controller navigation.
- [ ] Test performance.
