# GameNative Update & Login Refactor Plan

This plan outlines the changes required to update system image URLs to point to a new GitHub release and to remove the mandatory Steam login requirement, moving the login option to the settings/system menu.

## Proposed Changes

### Steam Service
#### [MODIFY] [SteamService.kt](file:///home/max/Build/GNP/Game/app/src/main/java/app/gamenative/service/SteamService.kt)
- Update `primaryUrl` (and `fallbackUrl` if needed) to point to `https://github.com/maxjivi05/Components/releases/download/Components/`.
- Specifically update the download logic for:
  - `imagefs_bionic.txz`
  - `imagefs_gamenative.txz`
  - `imagefs_patches_gamenative.tzst`
  - `steam.tzst`

### UI & Navigation
#### [MODIFY] [PluviaMain.kt](file:///home/max/Build/GNP/Game/app/src/main/java/app/gamenative/ui/PluviaMain.kt)
- Change `startDestination` in `NavHost` to `PluviaScreen.Home.route` by default.
- Modify the logic that redirects to `LoginUser` when not logged in, allowing the user to stay on the `Home` (Library) screen.
- Ensure `isOffline` mode is properly handled when starting without login.

#### [MODIFY] [SystemMenu.kt](file:///home/max/Build/GNP/Game/app/src/main/java/app/gamenative/ui/screen/library/components/SystemMenu.kt)
- Adjust the Steam login/logout buttons to be always available or clearly visible as an "Account" option, similar to Epic, GOG, and Amazon.
- Ensure the login button leads to the `UserLoginScreen` or triggers the login flow.

#### [MODIFY] [MainActivity.kt](file:///home/max/Build/GNP/Game/app/src/main/java/app/gamenative/MainActivity.kt)
- Review and adjust lifecycle management to ensure `SteamService` and other components initialize correctly even if the user is not logged in.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure the project builds successfully.
- Note: Network-dependent download tests cannot be fully automated here without mocking the server responses, which is out of scope for a quick fix.

### Manual Verification
1. **Direct Library Access**:
   - Open the app.
   - Verify it loads directly into the Library (Home screen) instead of the Steam login screen.
2. **Steam Login via Settings**:
   - Open the System Menu (START button or Top-Right icon).
   - Verify there is a "Sign in to Steam" button.
   - Click it and verify it opens the login screen.
3. **Download URLs**:
   - Try to install/download a component (if a fresh install).
   - Verify (via logs if possible) that it attempts to download from the new GitHub URL.
