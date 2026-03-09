# Walkthrough - Container Management & Launch Fixes

I have refactored the container management system and resolved critical game launch issues. These changes ensure a smoother experience with instant container readiness and a more stable launch process.

## Key Changes

### Instant Container Creation
- **Master Containers ON**: Containers are now created immediately after a Wine or Proton version is installed, rather than waiting for the first game launch.
- **Master Containers OFF**: Game-specific containers are now created instantly upon the completion of a game download from Steam, GOG, Epic, or Amazon.
- **Lazy Creation Removed**: All lazy container creation logic has been moved out of the launch sequence, reducing "Launching..." delays.

### Launch Stability & "Launching..." Fixes
- **Process Cleanup**: Added `forceKillWineServer()` to robustly shut down any stuck `wineserver`, `services.exe`, or `plugplay.exe` processes before a new launch. This prevents the common "device stuck at launching" state caused by zombie Wine processes.
- **Proper Dependencies**: 
    - Implemented explicit installation of **Mono** and **Gecko** using offline installers in the guest environment (`/opt/mono-gecko-offline`).
    - Installations are run with `/quiet /norestart` to prevent blocking dialogs while ensuring all required components are present.
- **Thread Safety**: Audited the entire launch sequence in `XServerScreen.kt` and wrapped all error callbacks that touch the UI in a Main thread handler. This prevents crashes (Force Closes) when errors occur during background setup.
- **Non-Blocking Launch**: Confirmed that all heavy IO and Wine setup operations run on a dedicated background executor (`WineSetup-Thread`).

## Files Modified

- [ContainerUtils.kt](file:///home/max/Build/GameNative-Performance/app/src/main/java/app/gamenative/utils/ContainerUtils.kt): Added `forceKillWineServer` and `createContainerInstantly`.
- [XServerScreen.kt](file:///home/max/Build/GameNative-Performance/app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt): Implemented process cleanup, dependency installation, and thread-safety fixes.
- [ContentsManager.java](file:///home/max/Build/GameNative-Performance/app/src/main/java/com/winlator/contents/ContentsManager.java): Hooked into content installation for instant master container creation.
- [SteamService.kt](file:///home/max/Build/GameNative-Performance/app/src/main/java/app/gamenative/service/SteamService.kt): Hooked into download completion.
- [GOGService.kt](file:///home/max/Build/GameNative-Performance/app/src/main/java/app/gamenative/service/gog/GOGService.kt), [EpicService.kt](file:///home/max/Build/GameNative-Performance/app/src/main/java/app/gamenative/service/epic/EpicService.kt), [AmazonService.kt](file:///home/max/Build/GameNative-Performance/app/src/main/java/app/gamenative/service/amazon/AmazonService.kt): Hooked into game installation for instant container creation.

## Verification Results

### Build Status
> [!NOTE]
> The build was performed and successfully completed via `assembleDebug`. 
> 
> During the build process, I identified and fixed several Kotlin compilation errors. I also resolved a pervasive CMake configuration failure by initializing a missing native Git submodule (`adrenotools`) and forcefully terminating hung build daemons.

### Stability Review
- Audited `PluviaApp.kt` and `MainActivity.kt` for potential UI hangs.
- Verified that heavy operations like container migration and Wine setup are correctly offloaded from the Main thread.
- Fixed several potential background-thread crashes in the error reporting logic.

---
*Created by Antigravity*
