# Advanced Container & Steam Integration

- [x] Modify "Game Settings" UI in `ContainerDetailFragment`
  - [x] Change "Wine Version" spinner to list existing containers instead of Wine packages when editing game settings
  - [x] Update save logic to modify the shortcut's `container_id` to switch containers
- [x] Investigate and fix `x86_64` container boot hang ("Starting..." infinite loop)
- [x] Integrate Steam Client & Online Fixes from GameNative
  - [x] Implement download and extraction of `steam.tzst`
  - [x] Implement Steamless / Goldberg emulator patching logic for games
- [/] Hook up SteamClientManager into application launch flow
  - [ ] Add Steam toggle to Container settings
  - [ ] Wire Steam extraction into XServerDisplayActivity boot sequence
  - [ ] Wire Steamless DRM into XServerDisplayActivity post-Wine-init
  - [ ] Wire Steam download into UI
- [/] Fix game launch to use per-game settings container and all settings
  - [ ] Ensure selected container from Game Settings is used when launching
  - [ ] Apply all per-game settings (drivers, components, etc.) from shortcut extras
  - [ ] Fix A: drive mapping so exe is found and launched from correct path
- [ ] Validate fixes with debug build
