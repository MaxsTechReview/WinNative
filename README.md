
# Winlator Ludashi – Game Genie crash fix (Asus game manager)

### Overview
This fork of Winlator Ludashi aims to fix a crash that occurs on my Rog Phone 9 Pro when the Game Genie (com.asus.gamewidget) service sends invalid key codes to the Winlator keyboard handler.
The primary purpose of this repository is to prepare a pull request that addresses this issue in the upstream project.

### Problem description
A fatal crash occurs in `Keyboard.onKeyEvent()` when Game Genie sends invalid keycodes (`851` or `861`) during the device's screen off/on lifecycle.

#### Exception details
```
FATAL EXCEPTION: main
Process: com.winlator
java.lang.ArrayIndexOutOfBoundsException: length=159 index=851
    at com.winlator.xserver.Keyboard.onKeyEvent(Keyboard.java:104)
Caused by: android.view.KeyEvent-JNI: java.lang.IllegalArgumentException: Invalid keycode 851
```

### Root cause
1. Game Genie (com.asus.gamewidget) generates invalid `keycodes` `851`/`861`.
2. `Keyboard.onKeyEvent(Keyboard.java:104)` uses `keys[keyCode]` without bounds checking.
3. Because the array length is `159,` accessing `keys[851]` triggers an `ArrayIndexOutOfBoundsException`.

### Proposed fix
Add a bounds check in `Keyboard.onKeyEvent()` before indexing the keys array to ignore invalid keycodes and prevent a crash.
```
if (keyCode < 0 || keyCode >= keycodeMap.length) {
            return false;
}
```

# Credits and Third-party apps

  - **Original Winlator** by [brunodev85](https://github.com/brunodev85/winlator)
  - **Original Winlator Bionic** by [Pipetto-crypto](https://github.com/Pipetto-crypto/winlator)
  - **Winlator (coffincolors fork)** by [coffincolors](https://github.com/coffincolors/winlator)
  - **Winlator Ludashi** by [StevenMX](https://github.com/StevenMXZ/Winlator-Ludashi)
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





