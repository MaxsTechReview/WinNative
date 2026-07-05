# FEX UnixLibs for WinNative — What It Is & How It Works

A short guide to the new **UnixLibs** feature: what changed, what gets installed,
and how the new `.so` files get loaded.

---

## The one-paragraph version

FEX (the x86→ARM translator used by ARM64EC containers) always ran as **Windows
DLLs living inside the container's C: drive**. Those DLLs are "boxed" inside
Wine's Windows world, so they can't talk to the Android kernel directly.
**UnixLibs** adds a small **native `.so`** that runs on the *Android side* and
gives FEX a direct line to the kernel — unlocking hardware-assisted x86 memory
ordering (TSO) and other speedups. Nothing about how you use containers changes;
it just runs faster where the hardware supports it.

---

## How it works (simple)

1. The FEX DLLs (`libwow64fex.dll` / `libarm64ecfex.dll`) stay in `system32` —
   they're still the entry point Wine loads.
2. On launch, each FEX DLL asks Wine: *"load my native helper by name."*
3. Wine finds the matching **`.so`** (`libwow64fex.so` / `libarm64ecfex.so`) in
   the running Wine/Proton's Unix library folder and loads it.
4. FEX now has a bridge from the boxed Windows side out to the Android kernel and
   uses it for the fast paths (see performance note at the bottom).

The DLL and the `.so` are a **pair** — the DLL loads the `.so`; they work
*together*, the `.so` does not replace the DLL.

---

## What's different from the old setup

| Piece | Old | New (UnixLibs) |
|---|---|---|
| **Proton** | Wine couldn't load a `.so` helper by name, so FEX silently fell back to the slower in-DLL path. | Proton's `ntdll` now supports **load-unixlib-by-name**, so it can find and load the `.so`. |
| **FEX version** | The FEX `.wcp` shipped **only** the two PE `.dll` files. | The FEX `.wcp` ships the `.dll` files **plus** the matching native **`.so`** files (built for Android/Bionic). |
| **WinNative APK** | Only copied the FEX `.dll` into `system32`. | Adds a **"Use UnixLibs" toggle** and, when on, copies the `.so` into the active Proton's Unix lib folder so Wine can find it. |

---

## What gets installed now

Three matching pieces are needed (all ARM64EC only):

1. **New Proton** — e.g. `Proton-11-B5-Arm64EC-Steam-UnixLibs` (its `ntdll`
   knows how to load the `.so`).
2. **New FEX component** — e.g. `FEX-2607-UnixLibs`, which contains:
   - `libwow64fex.dll`, `libarm64ecfex.dll` → copied into `system32` (as before)
   - `libwow64fex.so`, `libarm64ecfex.so` → the new native helpers
3. **New WinNative APK** — with the **Use UnixLibs** toggle and the wiring that
   places the `.so` where Wine looks for it.

Install the Proton and FEX components from **Components** like normal, pick them
in the container/shortcut, and make sure **Use UnixLibs** is on (it turns on
automatically when the selected FEX version includes `.so` files).

---

## How the `.so` files get loaded

- When you launch a container, WinNative copies the FEX `.so` files into the
  **active Proton's** `lib/wine/aarch64-unix/` folder (the exact place Wine
  searches). This happens per-launch, so it follows whichever Proton you pick.
- Wine's `ntdll` then loads the `.so` by name when FEX asks for it.
- Turning the toggle **off** removes the `.so` again, so FEX goes back to the
  plain DLL path.

**How to confirm it worked:** enable FEX/emulator logs, launch a game, and look
in the FEX log for:

```
FEX: Loaded FEXUnixLib
```

If you see that (once for each backend), the native helper is active. If it's
missing, FEX is running the old DLL-only path.

---

## What you might feel (performance)

FEX already translated x86 to native ARM either way — UnixLibs doesn't make
translation "native," it gives FEX **kernel access**. The main win is
**hardware TSO memory ordering**: FEX can drop the expensive software memory
barriers it normally needs for correct x86 behavior and let the CPU enforce it
instead — *fast and correct at the same time* (the same trick Apple's Rosetta
uses). Expect the biggest gains in memory- and multithread-heavy games, plus
smoother frame pacing.

Caveat: this is **hardware/kernel dependent**. On chips/kernels that expose the
memory-model toggle you'll feel it; on ones that don't, the `.so` still loads but
that speedup isn't available — no gain, but no regression either.
