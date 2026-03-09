# UnifiedActivity Redesign + Store Integration + Icon

## Summary

Comprehensive overhaul of `UnifiedActivity.kt` to deliver a PS5-like game carousel, adaptive multi-store tabs, Steam profile status selector, elegant shadow styling, and a new app icon.

## Proposed Changes

### 1. Header Bar Redesign

#### [MODIFY] [UnifiedActivity.kt](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/UnifiedActivity.kt)

**Top bar changes:**
- **Transparent background** — remove `Color(0xFF171A21)` from `Surface` → use `Color.Transparent`.
- **Tabs**: Reorder to `Library, Downloads, Steam` (AIO mode) or `Library, Downloads, Steam, Epic, GOG, Amazon` (non-AIO).
- **Adaptive shadow pill** behind the visible tabs — a `RoundedCornerShape(24.dp)` box with `shadow(8.dp, shape, spotColor=…)`.
- **Scrollable tabs** when > 4, using `LazyRow` with `snap` — show max 4, scroll for the rest.
- **Circle shadow behind Settings (left) and Controller (right) buttons** — wrap each `IconButton` in a `Box` with `shadow(6.dp, CircleShape)`.

**Steam profile button:**
- Add a 3rd button to the right of the Controller icon: a circular `AsyncImage` showing the Steam avatar (from `localPersona.avatarHash`).
- On click, show a `DropdownMenu` with Online / Away / Invisible status options (matching `SystemMenu.kt` pattern).
- Calls `SteamService.setPersonaState(state)`.

**AIO mode toggle:**
- Add a `PrefManager` boolean `aioStoreMode` (default `true`).
- When `true`: 3 tabs (Library, Downloads, Store). When `false`: 6 tabs (Library, Downloads, Steam, Epic, GOG, Amazon).

---

### 2. PS5-Style Game Carousel (Library Tab)

Replace `LazyVerticalGrid` with a horizontal `LazyRow` carousel:
- **~4 games visible** at a time.
- **Center item "pops up"** — use `graphicsLayer { scaleX/Y }` animated with `animateFloatAsState`. Center item scales to `1.15f`, others `0.9f`.
- **Drop shadow on highlighted item** via `Modifier.shadow(16.dp, RoundedCornerShape(12.dp))`.
- **Snap-to-center** using `SnapFlingBehavior` with `rememberSnapLayoutInfoProvider`.
- **Item size**: ~200dp wide × 280dp tall (capsule art ratio).

**Artwork fix:**
- Prioritize `getHeaderImageUrl()` first (most reliable), then fallback chain: `getCapsuleUrl()` → `iconUrl` → Steam CDN community URL.
- Add `crossfade(true)` and `placeholder` to Coil `AsyncImage`.

---

### 3. Store Tabs Content

- **Steam tab**: existing `SteamStoreTab` (already works).
- **Epic / GOG / Amazon tabs**: Placeholder content with a "Sign In" button that launches a WebView-based OAuth flow (ported from GameNative services). Full store browsing is deferred to a follow-up — this phase adds sign-in/out only, matching the "Stores" menu requirement.

---

### 4. Avatar URL Utility

#### [NEW] [StringUtils.kt](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/steam/utils/StringUtils.kt)
- Port `String.getAvatarURL()` extension from GameNative.

---

### Fix Container Creation Crashing

We've deeply debugged the container creation flow to find out why it was forcefully returning to the Unified Activity. The problem was an uncovered edge case causing the application to crash completely in the background. 

## Proposed Changes

### [WinNative ContainerManager]
`ContainerManager.java` uses `Executors.newSingleThreadExecutor()` to asynchronously construct containers in the background so the UI doesn't freeze.
During creation, it attempts to extract DLLs from the main Wine file system via `extractCommonDlls()`. To do this, it calls `File[] srcfiles = srcDir.listFiles(...)` on `/lib/wine/...` paths.
**The Bug**: If these folders are not fully structured identical to what the method expects (for example, missing `arm64ec` paths), `listFiles()` returns `null`. Attempting to loop over `null` instantly triggers an uncaught `NullPointerException`.
**The Crash**: Because this happens on an unhandled background thread, the entire Android application crashes silently. Android's built-in crash recovery then automatically re-launches the application natively at the root `UnifiedActivity` (the Library Tab).

#### [MODIFY] [ContainerManager.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/container/ContainerManager.java)
- **Null Safety**: Add `if (srcfiles != null)` guards before iterating in `extractCommonDlls`.
- **Global Error Handling**: Broaden the error catching in `createContainer` from `catch (JSONException e)` to `catch (Exception e)`. If ANY future error occurs (e.g. disk space full during extraction, missing assets), the background thread will catch it, return `null`, and gracefully trigger a Toast warning (`Unable to install system files`) instead of forcefully closing the application.

### The Real Root Cause: Missing Wine Assets

Although the code logic was technically crashing from `NullPointerException`, the *reason* why `/lib/wine/` folders did not exist in the first place is that the **`proton-9.0-x86_64.txz` and `proton-9.0-arm64ec.txz` archives were entirely missing from the `WinNative/app/src/main/assets/` folder**, even though they existed in `Winlator-Ludashi`.

Because `ImageFsInstaller.java` silently traps exceptions on asset extraction (`try { TarCompressorUtils.extract(...) } catch (Exception e) {}`), the system files installer completed successfully WITHOUT installing the actual Wine binaries.

- **Solution**:
    - **Asset Copying**: Copied `proton-9.0-arm64ec.txz` (62MB) and `proton-9.0-x86_64.txz` (48MB) from `Winlator-Ludashi/app/src/main/assets/` to `WinNative/app/src/main/assets/`.
    - **Forced Reinstallation**: Incremented `LATEST_VERSION` to `22` inside `ImageFsInstaller.java` so that on the next app launch, testing devices will automatically extract the now-included `.txz` files.

---

## Verification Plan

### Automated
- `./gradlew assembleDebug --info`

### Manual
- Verify carousel scrolling, center pop-up effect, and shadow.
- Verify tab reorder, AIO toggle, scrolling tabs.
- Verify Steam avatar + status picker.
- Verify icon on launcher.
