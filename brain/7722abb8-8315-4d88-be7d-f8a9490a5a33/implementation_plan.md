# Implementation Plan - Container Management & Launch Fixes

Refactor Proton/Wine container creation logic to be instant rather than lazy, and address "launching..." stalls by suppressing interactive prompts and ensuring clean process states.

## User Review Required

> [!IMPORTANT]
> - Instead of bypassing Mono and Gecko installation, we will ensure they are installed properly using offline installers located in `Z:\opt\mono-gecko-offline`.
> - For "Master Containers ON", creation happens once per Proton/Wine version.
> - For "Master Containers OFF", a container is created for each game immediately after its installation completes.

## Proposed Changes

### [Component Name] Container Management

#### [MODIFY] [ContainerUtils.kt](file:///home/max/Build/GameNative-Performance/app/src/main/java/app/gamenative/utils/ContainerUtils.kt)
- Add `forceKillWineServer()` function to robustly clean up any zombie `wineserver` or `services.exe` processes before a new launch.
- Ensure `getOrCreateContainer` and related heavy logic runs on `Dispatchers.IO` to prevent UI thread hangs.

#### [MODIFY] [ContentsManager.java](file:///home/max/Build/GameNative-Performance/app/src/main/java/com/winlator/contents/ContentsManager.java)
- In `finishInstallContent`, if the installed content is `WINE` or `PROTON` and `Master Containers` is ON, trigger instant creation of the master container.

#### [MODIFY] [SteamService.kt](file:///home/max/Build/GameNative-Performance/app/src/main/java/app/gamenative/service/SteamService.kt)
- In `notifyDownloadStopped` (on success), if `Master Containers` is OFF, trigger instant container creation for the installed game.

#### [MODIFY] [GOGService.kt](file:///home/max/Build/GameNative-Performance/app/src/main/java/app/gamenative/service/gog/GOGService.kt)
- In `downloadGame` success block, if `Master Containers` is OFF, trigger instant container creation.

#### [MODIFY] [EpicService.kt](file:///home/max/Build/GameNative-Performance/app/src/main/java/app/gamenative/service/epic/EpicService.kt)
- In `downloadGame` success block, if `Master Containers` is OFF, trigger instant container creation.

#### [MODIFY] [AmazonService.kt](file:///home/max/Build/GameNative-Performance/app/src/main/java/app/gamenative/service/amazon/AmazonService.kt)
- In `downloadGame` success block, if `Master Containers` is OFF, trigger instant container creation.

### [Component Name] Launch Stability

#### [MODIFY] [XServerScreen.kt](file:///home/max/Build/GameNative-Performance/app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt)
- In `unpackExecutableFile`, ensure Mono (and Gecko if found) installers are run with `/quiet /norestart`.
- Call `ContainerUtils.forceKillWineServer()` before starting the `XEnvironment` and after dependency installations to ensure clean state.

## Verification Plan

### Manual Verification
1. **Master Containers ON**:
    - Uninstall a Wine/Proton version.
    - Re-install it from "Component Manager".
    - Verify in "Containers" list that a "Master" container is created immediately after installation finishes.
2. **Master Containers OFF**:
    - Install a small game from Steam/GOG/Epic.
    - Verify that a container for that game is created immediately after the download reaches 100%.
3. **Launch Stability**:
    - Launch a game that previously got stuck at "launching...".
    - Verify it proceeds to the game or redist installation screen without hanging.
    - Check logcat for `forceKillWineServer` execution logs.
4. **App Stability**:
    - Navigate quickly through the library and settings while installations are active.
    - Verify no ANRs or force closes occur due to UI thread blocking.

### Automated Tests
- Build the project using `./gradlew assembleDebug` to ensure no regressions in compilation.
