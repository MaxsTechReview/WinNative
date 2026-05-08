<p align="center">
  <img src="logo.png" alt="WinNative" width="500">
</p>
<p align="center">
    <a href="https://discord.gg/uhTkvGfakU">
        <img src="https://img.shields.io/discord/1358831699814912141?color=5865F2&label=WinNative&logo=discord&logoColor=white"
            alt="Discord">
    </a>
</p>

## WinNative: A Community Built Windows Emulation App for Android

**WinNative** is an advanced, high-performance Windows (x86_64) emulation environment for Android. It bridges the gap between desktop gaming and mobile by unifying the best technologies from **Winlator Bionic** and **Pluvia**.

Designed for enthusiasts and power users, WinNative delivers the full Winlator experience while making it easy to connect your Steam, Epic, and GOG game libraries.

---

### Installation

1. **Download:** Get the latest APK from the [Releases](https://github.com/maxjivi05/WinNative/releases) section.
2. **Variants:**
   - `Ludashi`: Best for Xiaomi/RedMagic (Performance Mode trigger).
   - `Vanilla`: Standard package name for side-loading with other forks.
3. **Setup:** Launch the app, allow the ImageFS to install, and start adding your games manually or sync your library. 

---

### How to Build

**Requirements:** Android Studio, JDK 17, NDK `30.0.14904198`, and CMake.

1. **Clone the repository and update submodules** (Required):
   ```bash
   git clone https://github.com/MaxsTechReview/WinNative.git
   cd WinNative
   git submodule update --init --recursive
   ```
2. **Build via Android Studio:** Open the `WinNative` directory, let Gradle sync, then select **Build > Build APK(s)**.
3. **Build via CLI:** Run `.\gradlew.bat :app:assembleStandardDebug` (Windows).

---

### Controller Vibration

WinNative now supports external controller rumble through two paths:

1. **Standard Android controller vibration**
   - Uses `InputDevice` vibrator APIs, including `VibratorManager` on newer Android versions.
   - Covers controllers that expose vibration through the normal Android input stack.

2. **USB Xbox360-style rumble fallback**
   - Requests USB host permission for compatible external controllers.
   - Opens Xbox360-style USB controller interfaces directly and sends rumble packets when the Android input stack does not expose a usable vibrator.
   - This path is intended for newer Android devices and controllers that enumerate as generic Xbox pads or require app-level USB access for rumble.

This behavior was validated on a real device/controller combination where standard Android vibration alone was not sufficient.

---

### Contributing

We welcome community contributions! Feel free to open a pull request for bug fixes, driver updates, UI improvements, or anything else you'd like to add.

Please match the existing code style and ensure any AI-assisted code is thoroughly reviewed and tested before submission.

---

### Credits & Acknowledgments

- **Original Winlator** by [brunodev85](https://github.com/brunodev85/winlator)
- **Winlator Bionic** by [Pipetto-crypto](https://github.com/Pipetto-crypto/winlator)
- **Pluvia** features by the [Pluvia](https://github.com/oxters168/Pluvia) / [GameNative](https://github.com/utkarshdalal/GameNative) community
- **Mesa/Turnip** contributions by the [Mesa3D](https://www.mesa3d.org/) team
- **Goldberg Steam Emulator** by [Mr. Goldberg](https://gitlab.com/Mr_Goldberg/goldberg_emulator), maintained by [Detanup01](https://github.com/Detanup01/gbe_fork)
