# Frontend View Redesign — Steam Deck-Style

## Build Status
```
BUILD SUCCESSFUL in 24s — 81 actionable tasks
```

## What Changed

### Complete Rewrite: [LibraryFrontendPane.kt](file:///home/max/Build/GNP/Game/app/src/main/java/app/gamenative/ui/screen/library/components/LibraryFrontendPane.kt)

**Dark theme base** — `Color(0xFF0E0E10)` background matching Steam Deck's dark aesthetic

**Frosted glass tab bar** (`FrontendTopBar`)
- Gradient overlay at top fading from dark → transparent
- **L1/R1 button hints** flanking the tabs (bordered badges)
- Animated accent underline on selected tab, dimmed unselected tabs
- Clickable tabs for touch, L1/R1 for controller

**Hero spotlight** (`FrontendLibraryView`)
- Full-bleed hero artwork with vertical + horizontal gradient scrims
- Game source badge (colored dot + "STEAM" / "EPIC GAMES" / etc.)
- Large bold title with drop shadow readability
- Accent Play button with elevation and rounded corners
- "YOUR GAMES" label above the carousel

**Carousel cards** (`FrontendCarouselCard`)
- 170×96dp compact 16:9 tiles
- **Focus glow ring** — radial gradient behind focused card
- **Scale animation** — 1.08× on focus with spring physics
- **Accent border** — primary color border on focus
- Dark fallback card with game name if image fails
- Subtle bottom gradient for readability

**AIO Store toggle** 
- `aioStoreMode = true` → Library / Store / Downloads (Store = all sources merged)
- `aioStoreMode = false` → Library / Steam / Epic / GOG / Amazon / Downloads

**Store grid view** (`FrontendStoreView`)
- 4-column grid using existing `AppItem` with `PaneType.GRID_HERO`
- Pagination via `snapshotFlow` observer

**Empty/loading states**
- Gamepad icon + descriptive text for empty library, empty store, no downloads
- Spinner + "Loading library…" text during initial load

### Default Layout: [LibraryScreen.kt](file:///home/max/Build/GNP/Game/app/src/main/java/app/gamenative/ui/screen/library/LibraryScreen.kt#L291-L297)
- `UNDECIDED` now defaults to `PaneType.FRONTEND`
