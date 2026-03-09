# Turnip v2.2.0 Gen1 Profile Fix & Rebuild Walkthrough

## Problem
The v2.2.0 drivers were crashing emulators on Adreno Gen1 chips (A810, A825, A829, A830) due to incorrect GPU profiles in `freedreno_devices.py`.

## Root Causes Identified & Fixed

| Issue | Affected Chips | Fix |
|---|---|---|
| `reg_size_vec4 = 128` override in `a8xx_gen1` | A810, A825, A829, A830 | Removed — inherits correct `96` from `a8xx_base` |
| `sysmem_ccu_depth_cache_fraction = FULL` | All Gen1 | Changed to `THREE_QUARTER` |
| `disable_gmem = False` on A830 | A830 | Set to `True` (fixes black boxes) |
| `raw_magic_regs = a8xx_gen2_raw_magic_regs` | A810, A825, A829, A830 | Changed to `a8xx_base_raw_magic_regs` |

## Files Modified
- [freedreno_devices.py](file:///home/max/Build/Turnip/mesa-v2.2.0-source/src/freedreno/common/freedreno_devices.py) — Lines 1506–1637

## Build Results
Both variants compiled and packaged successfully:

| Variant | Package | Size |
|---|---|---|
| **Balanced** (`-b`) | `Turnip_MTR_v2.2.0-b_Axxx.zip` | 2.7 MB |
| **Performance** (`-p`) | `Turnip_MTR_v2.2.0-p_Axxx.zip` | 2.7 MB |

Output: `/home/max/Build/Turnip/`

## Additional Fixes (UBWC v5.0 / v6.0)
- **Issue:** The Adreno 840 (and other Gen2 variants) use newer Universal Bandwidth Compression formats. The Turnip driver in `tu_knl_kgsl.cc` was missing cases for `KGSL_UBWC_5_0` and `KGSL_UBWC_6_0` when querying `KGSL_PROP_UBWC_MODE` from the kernel. This caused a fatal `VK_ERROR_INITIALIZATION_FAILED` on startup which crashed all games.
- **Fix:** Added missing cases `KGSL_UBWC_5_0` and `KGSL_UBWC_6_0` to default to `FDL_MACROTILE_8_CHANNEL` layout.
- **Files Modified:** [tu_knl_kgsl.cc](file:///home/max/Build/Turnip/mesa-v2.2.0-source/src/freedreno/vulkan/tu_knl_kgsl.cc) (Lines 1902-1906)

## v2.3.0 Synchronization & Yuzu Emulator Fixes
- **Issue:** Yuzu/Eden emulators were hanging at 0 FPS (black screen deadlock but no hard crash) on Adreno 840 devices when using Turnip v2.3.0. 
- **Root Cause & Fix:** 
  1. The new KGSL queue sync `TU_KGSL_SYNC_IMPL_TYPE_TIMELINE` was found to be incompatible with generic Android display queues and dma_fence. We explicitly reverted Turnip to wait for standard `TU_KGSL_SYNC_IMPL_TYPE_SYNCOBJ`.
  2. The implicit KGSL submission (command buffer cache flush) being removed via omitting `TU_DEBUG_FLUSHALL` caused an emulator display queue wait lock causing 0 FPS. We directly patched `tu_device.cc` to inject `tu_env.debug |= TU_DEBUG_FLUSHALL;` strictly when `device->gmem_size` matches the Adreno 830 and Adreno 840 targets, avoiding the broad A8XX stability issues of the past.
- **Result:** Both the balanced and performance variants compiled successfully to `/home/max/Build/Turnip/`.

| Variant | Package | Size |
|---|---|---|
| **Balanced** (`-b`) | `Turnip_MTR_v2.3.0-b_Axxx.zip` | 2.8 MB |
| **Performance** (`-p`) | `Turnip_MTR_v2.3.0-p_Axxx.zip` | 2.8 MB |
