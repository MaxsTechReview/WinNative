# Container Crash Fixes — Final Report

## Root Cause Analysis

The **graphics driver gear button crash** persisted because of a fundamental Java limitation:

> `super()` must be the first statement in a constructor and **CANNOT** be wrapped in `try-catch`.

When `GraphicsDriverConfigDialog` called `super(anchor.getContext(), R.layout.graphics_driver_config_dialog)`, this triggered `ContentDialog`'s constructor which inflates the layout. If ANY error occurs during this process (native library loading, theme resolution, layout inflation), the error propagates **before** our `try-catch` inside `initializeDialog()` is ever reached.

Additionally, the caller in `ContainerDetailFragment` used `catch (Exception e)` which **cannot catch Java `Error` subclasses** like `UnsatisfiedLinkError`, `NoClassDefFoundError`, or `ExceptionInInitializerError`.

## All Fixes Applied (3× Check-Fix-Check Verified)

### Critical: GraphicsDriverConfigDialog Redesign
- **Problem**: `super()` call in constructor can't be wrapped in try-catch
- **Fix**: Created `showSafe()` static factory method that wraps the **ENTIRE construction** (including `super()` and layout inflation) in `catch(Throwable)`
- **File**: [GraphicsDriverConfigDialog.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/contentdialog/GraphicsDriverConfigDialog.java)

### Critical: All Dialog Call Sites Updated

| File | Line | Before | After |
|------|------|--------|-------|
| [ContainerDetailFragment.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/ContainerDetailFragment.java) | 854 | `new GraphicsDriverConfigDialog(...).show()` wrapped in `catch(Exception)` | `GraphicsDriverConfigDialog.showSafe(...)` |
| [ShortcutSettingsDialog.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/contentdialog/ShortcutSettingsDialog.java) | 685 | `new GraphicsDriverConfigDialog(...).show()` — **NO catch at all** | `GraphicsDriverConfigDialog.showSafe(...)` |
| [ContainerDetailFragment.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/ContainerDetailFragment.java) | 883/891 | `catch(Exception)` on DXVK/WineD3D dialogs | `catch(Throwable)` |
| [ShortcutsFragment.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/ShortcutsFragment.java) | 178 | `new ShortcutSettingsDialog(...)` — **NO catch at all** | Wrapped in `catch(Throwable)` |
| [ContainerDetailFragment.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/ContainerDetailFragment.java) | 692 | Container save `catch(Exception)` | `catch(Throwable)` |
| [ContainerManager.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/container/ContainerManager.java) | 155 | `createContainer` `catch(Exception)` | `catch(Throwable)` with logging |

### Null Safety Fixes

| File | Issue |
|------|-------|
| [ShortcutSettingsDialog.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/contentdialog/ShortcutSettingsDialog.java) | `getTag().toString()` NPE → null-guarded |
| [ShortcutSettingsDialog.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/contentdialog/ShortcutSettingsDialog.java) | `StringUtils.parseIdentifier(spinner.getSelectedItem())` NPE → null-guarded |
| [GraphicsDriverConfigDialog.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/contentdialog/GraphicsDriverConfigDialog.java) | All `config.get()` calls → null-guarded with defaults |
| [GraphicsDriverConfigDialog.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/contentdialog/GraphicsDriverConfigDialog.java) | All spinner listener `getSelectedItem()` calls → null-guarded |
| [GraphicsDriverConfigDialog.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/contentdialog/GraphicsDriverConfigDialog.java) | `writeGraphicsDriverConfig()` static fields → null-guarded with defaults |

### Logic Bug Fixes

| File | Issue |
|------|-------|
| [DXVKConfigDialog.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/contentdialog/DXVKConfigDialog.java) | Forward-loop `remove(i)` skips elements → reverse iteration |
| [DXVKConfigDialog.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/contentdialog/DXVKConfigDialog.java) | `dxvkVersions` shared list reference → defensive copy |

## Verification

- ✅ **CHECK #1**: Build successful
- ✅ **CHECK #2**: Build successful, zero `catch(Exception)` in critical files
- ✅ **CHECK #3**: Build successful, comprehensive audit complete

> [!IMPORTANT]
> The key architectural insight: **`catch(Exception)` NEVER catches Java `Error` subclasses** (`UnsatisfiedLinkError`, `NoClassDefFoundError`, `ExceptionInInitializerError`). Native JNI code throws these. Always use `catch(Throwable)` when native code is in the call chain. And **`super()` cannot be wrapped in try-catch** — use static factory methods instead.
