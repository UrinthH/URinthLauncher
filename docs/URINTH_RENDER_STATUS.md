# URinthRender implementation status

This document distinguishes code that exists from work that still needs device validation.

## Implemented in the launcher

- URinthUltra Mode is an opt-in launch profile; the existing selected RenderSpec remains the native graphics backend.
- The Ultra wrapper delegates renderer compatibility checks, library paths, environment setup, and renderer setup to the selected backend.
- Mesa/Zink-family profiles configure shader-cache environment variables and choose the Mesa worker-thread setting based on Android's low-RAM signal / available memory information.
- GL4ES batching is opt-in under Ultra Mode.
- Launch diagnostics record device/ABI/RAM information, the selected backend, setup duration, and fallback outcomes.
- Renderer-cache cleanup is safe when called before a compatibility scan and can be repeated.
- The launcher must not change the player's resolution or edit `options.txt`.

## Important limitations

- The wrapper is not a new native OpenGL or Vulkan driver. It does not replace GL4ES, LTW, Zink, Mesa, or Turnip.
- Environment overrides are not proof of improved performance. The effect depends on backend build, GPU/driver, Minecraft version, mods, and workload.
- A successful Android build confirms compilation and packaging only; it does not prove game startup, rendering correctness, stability, or FPS gains.
- Sodium and VulkanMod compatibility has not been established universally. Treat each version/backend/mod combination as unverified until tested.
- The current launcher-side code does not by itself implement Minecraft's in-game chunk scheduling or mod-level rendering optimizations.

## Required validation matrix

Record the exact values for every run; do not infer capability from a GPU name alone.

| Field | Record |
|---|---|
| Device | Manufacturer, model, Android version, RAM |
| Graphics | GPU vendor/renderer and actual game-context OpenGL/Vulkan capabilities |
| Software | Launcher commit, Minecraft version, Java runtime, backend and backend version |
| Mods | Sodium/VulkanMod version, dependencies, other rendering mods |
| Workload | Same world/scene, render distance, shader pack, resource pack, warm-up and run duration |
| Results | Average FPS, 1% low if available, frame-time percentiles/spikes, memory pressure, crashes/visual defects |

## Acceptance gates

1. Install and launch the APK on the target device with Ultra Mode both OFF and ON.
2. Confirm the selected backend and fallback messages in logs; verify the game actually reaches a world.
3. Repeat the same workload several times with Ultra OFF/ON. Compare frame times as well as average FPS; do not claim a gain from a single run.
4. Test Sodium and VulkanMod separately for each supported Minecraft/backend combination. Mark unsupported combinations explicitly.
5. Verify player resolution and `options.txt` remain unchanged.
6. Only promote a profile from experimental after repeatable results and visual/stability checks.
