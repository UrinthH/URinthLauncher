# URinthRender implementation status

This document distinguishes code that exists from work that still needs device validation.

## Implemented in the launcher

- URinthUltra Mode is an opt-in launch profile; the existing selected RenderSpec remains the native graphics backend.
- The Ultra wrapper delegates renderer compatibility checks, library paths, environment setup, and renderer setup to the selected backend.
- Mesa/Zink-family profiles configure shader-cache environment variables and choose the Mesa worker-thread setting based on Android's low-RAM signal / available memory information.
- GL4ES batching is opt-in under Ultra Mode.
- Launch diagnostics record device/ABI/RAM information, the selected backend, setup duration, and fallback outcomes.
- Renderer environment application now reads each configured value back with `Os.getenv` and logs expected/actual values. For GL4ES Ultra mode, it specifically reports whether `LIBGL_BATCH=1` is present after the launcher applies the environment.
- Renderer-cache cleanup is safe when called before a compatibility scan and can be repeated.
- The launcher must not change the player's resolution or edit `options.txt`.

## Important limitations

- The wrapper is not a new native OpenGL or Vulkan driver. It does not replace GL4ES, LTW, Zink, Mesa, or Turnip.
- Environment read-back only verifies the launcher process environment immediately after `setenv`; it does not prove a backend reads or honors a variable.
- Environment overrides are not proof of improved performance. The effect depends on backend build, GPU/driver, Minecraft version, mods, and workload.
- `RendererFrameTimeStats` is a tested bounded statistics accumulator, but it is not currently connected to Minecraft's live render loop. Its unit tests are not live FPS tests.
- A successful Android build confirms compilation and packaging only; it does not prove game startup, rendering correctness, stability, or FPS gains.
- Sodium and VulkanMod compatibility has not been established universally. Treat each version/backend/mod combination as unverified until tested.
- The current launcher-side code does not by itself implement Minecraft's in-game chunk scheduling or mod-level rendering optimizations.

## Next engineering gates

1. Confirm the environment verification logs on a real GL4ES launch with Ultra OFF and ON.
2. Identify a reliable frame-presentation or game-loop integration point before connecting frame-time statistics. Do not label launcher startup timing as game FPS.
3. Implement and validate one backend-specific change only after instrumentation identifies a bottleneck; keep it reversible and compare repeated frame-time runs.
4. Avoid unsupported assumptions about native hooks or variables. If a variable is accepted by `setenv`, that alone is not proof the backend implements it.

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
