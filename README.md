

\<p align="center"\>
\<img src="logo.png" alt="Winlator Bionic" width="600"\>
\</p\>

# Winlator Bionic

Winlator is an Android application that lets you run Windows (x86\_64) applications with Wine. It supports standard `x86_64` containers using Box86/Box64, as well as `Arm64EC` containers which utilize FEXCore (for 64/32-bit) or an optional WowBox64 (for 32-bit).

This is a fork of the **Winlator Bionic** project by [Pipetto-crypto](https://github.com/Pipetto-crypto/winlator).

## What is the Ludashi Build?

The Ludashi Build is functionally identical to the standard Bionic app, but the package name has been renamed to mimic Ludashi, a popular benchmark app. Some Android phones — especially Xiaomi devices — may automatically enable performance mode when such apps are detected, potentially reducing throttling and boosting performance slightly.

# Installation

1.  Download and install the latest APK from this repository's [Releases section](https://github.com/StevenMXZ/Winlator-Ludashi/releases) (choose either the regular Bionic or the Ludashi build).
2.  Launch the app and wait for the installation process to finish.

# Useful Tips

  - Here is a tutorial from the Do ZeroKimchi channel on how to use Winlator Bionic:
    [](https://www.google.com/search?q=%5Bhttps://youtu.be/EJDWZUGF9sk%5D\(https://youtu.be/EJDWZUGF9sk\))
  - If you are using an `x86_64` container and experiencing performance issues, try changing the Box86/Box64 preset to **Performance** in Container Settings -\> Advanced Tab.
  - If you are using an `Arm64EC` container, try swapping between different FEX-Emu versions in the container settings for better compatibility or performance.
  - For applications that use .NET Framework, try installing Wine Mono found in Start Menu -\> System Tools.
  - If some older games don't open, try adding the environment variable MESA\_EXTENSION\_MAX\_YEAR=2003 in Container Settings -\> Environment Variables.
  - Try running the games using the shortcut on the Winlator home screen, there you can define individual settings for each game.
  - To speed up the installers, try changing the Box86/Box64 preset to Intermediate in Container Settings -\> Advanced Tab.

# Credits and Third-party apps

  - **Original Winlator** by [brunodev85](https://github.com/brunodev85/winlator)
  - **Original Winlator Bionic** by [Pipetto-crypto](https://github.com/Pipetto-crypto/winlator)
  - **Winlator (coffincolors fork)** by [coffincolors](https://github.com/coffincolors/winlator)
  - Ubuntu RootFs (Bionic Beaver): [releases.ubuntu.com/bionic](https://www.google.com/search?q=https://releases.ubuntu.com/bionic)
  - Wine: [winehq.org](https://www.winehq.org/)
  - Box86/Box64 by [ptitseb](https://github.com/ptitSeb)
  - FEX-Emu by [FEX-Emu](https://github.com/FEX-Emu/FEX)
  - PRoot: [proot-me.github.io](https://proot-me.github.io)
  - Mesa (Turnip/Zink/VirGL): [mesa3d.org](https://www.mesa3d.org)
  - DXVK: [github.com/doitsujin/dxvk](https://github.com/doitsujin/dxvk)
  - VKD3D: [gitlab.winehq.org/wine/vkd3d](https://gitlab.winehq.org/wine/vkd3d)
  - D8VK: [github.com/AlpyneDreams/d8vk](https://github.com/AlpyneDreams/d8vk)
  - CNC DDraw: [github.com/FunkyFr3sh/cnc-ddraw](https://github.com/FunkyFr3sh/cnc-ddraw)

Many thanks to [ptitseb](https://github.com/ptitSeb) (Box86/Box64), [Danylo](https://blogs.igalia.com/dpiliaiev/tags/mesa/) (Turnip), [alexvorxx](https://github.com/alexvorxx) (Mods/Tips) and others.

Thank you to
all the people who believe in this project.
