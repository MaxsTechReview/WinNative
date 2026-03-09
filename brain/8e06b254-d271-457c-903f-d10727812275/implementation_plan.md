# PS5-Style Frontend Redesign

## Goal Description
Redesign the main frontend of GameNative to a modern PlayStation 5 aesthetic. The UI will feature a top‑centered header and three primary tabs: **Library**, **Store**, and **Downloads**. The Store tab will aggregate all supported game stores into a single list view. The Library tab will display installed games using a card‑style layout. The Downloads tab will act as a download manager showing active downloads.

## Proposed Changes
---
### Layout & UI Components
- **Create new layout** `activity_main_ps5.xml` in `res/layout` defining a `CoordinatorLayout` with a centered `Toolbar` and a `TabLayout` + `ViewPager2` for navigation.
- **Add style resources** in `res/values/styles.xml` for a dark theme, custom colors, and typography matching PS5 UI (e.g., gradient background, neon accent colors).
- **Define color palette** in `res/values/colors.xml` (dark background, primary accent, secondary accent, text colors).
- **Create fragment layouts**:
  - `fragment_library_ps5.xml` – RecyclerView with card items displaying game cover art, title, and play button.
  - `fragment_store_ps5.xml` – Single list aggregating games from all stores, using the same card UI.
  - `fragment_downloads_ps5.xml` – ListView/RecyclerView showing current download progress with progress bars.
- **Add new fragment classes** in Kotlin:
  - `LibraryFragmentPS5.kt`
  - `StoreFragmentPS5.kt`
  - `DownloadsFragmentPS5.kt`
- **Update MainActivity** to use the new layout and set up `ViewPager2` with the three fragments.
- **Add animations** (fade‑in, slide transitions) via XML animator resources for premium feel.
- **Add icons** (SVG/VectorDrawable) for tab icons matching PS5 style.

---
### Resources
- Add font `Inter` (or similar) via Google Fonts in `res/font` and reference it in the theme.
- Add background image (gradient) in `drawable` for the main container.

---
## Verification Plan
### Automated Tests
- **UI Test**: Use AndroidX `Espresso` to launch `MainActivity` and verify that the `TabLayout` contains three tabs with correct titles.
- **Fragment Test**: Verify each fragment inflates without crash.
- **RecyclerView Test**: Populate mock data and assert that the RecyclerView displays the expected number of items.

### Manual Verification
1. Build and run the app (`./gradlew assembleDebug`).
2. Observe the new PS5‑style UI:
   - Header centered with title.
   - Tabs correctly labeled and switchable.
   - Library shows installed games as cards.
   - Store aggregates all stores.
   - Downloads shows active downloads.
3. Test controller navigation (DPAD) to move between tabs and select items.
4. Verify animations are smooth and no performance regressions.

---
**User Review Required**
- Confirm the proposed color palette and typography.
- Approve creation of new fragment classes and layout files.
- Approve the verification approach (automated UI tests + manual steps).
