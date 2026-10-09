# URinthRender implementation status

This document distinguishes code that exists from work that still needs device validation.

## Implemented in the launcher

- URinthUltra Mode is an opt-in launch profile; the existing selected RenderSpec remains the native graphics backend.
- The Ultra wrapper delegates renderer compatibility checks, library paths, environment setup, and renderer setup to the selected backend.
- Mesa/Zink-family profiles configure shader-cache environment variables and choose the Mesa worker-thread setting based on Android's low-RAM signal / available memory information.
- No GL4ES-specific performance override is enabled until a repeatable device benchmark demonstrates a benefit and compatibility.
- Launch diagnostics record device/ABI/RAM information, the selected backend, setup duration, and fallback outcomes.
- Renderer environment application reads each configured value back with `Os.getenv` and logs expected/actual values. This is launcher-process diagnostics only.
- Renderer-cache cleanup is safe when called before a compatibility scan and can be repeated.
- The launcher must not change the player's resolution or edit `options.txt`.

## Important limitations

- The wrapper is not a new native OpenGL or Vulkan driver. It does not replace GL4ES, LTW, Zink, Mesa, or Turnip.
- Environment read-back only verifies the launcher process environment immediately after `setenv`; it does not prove a backend reads or honors a variable.
- Environment overrides are not proof of improved performance. The effect depends on backend build, GPU/driver, Minecraft version, mods, and workload.
- `RendererFrameTimeStats` is a tested bounded statistics accumulator, but it is not currently connected to Minecraft's live render loop. Its unit tests are not live FPS tests.
- A successful Android build confirms compilation and packaging only; it does not prove game startup, rendering correctness, stability, or FPS gains.
- **Sodium support is a required target**, but it is not guaranteed by the URinthUltra wrapper alone. Sodium runs inside Minecraft and depends on the exact Minecraft version, mod loader, Java/runtime setup, and graphics capabilities exposed by the selected backend. Validate it per combination; do not claim that GL4ES or every OpenGL translation path satisfies Sodium's requirements.
- **VulkanMod support is a separate required target**, not an automatic consequence of enabling Ultra Mode. It requires a working Vulkan path with the Vulkan capabilities the mod expects, compatible LWJGL Vulkan bindings/native libraries for the device ABI, and a compatible Minecraft/mod-loader/mod version. An OpenGL-only backend such as GL4ES cannot be treated as a Vulkan backend. Keep unsupported combinations explicitly marked unsupported instead of silently forcing them.
- Neither mod is made compatible merely by setting URinthUltra environment variables. The wrapper must preserve the user's selected backend and avoid injecting backend-specific overrides unless that exact combination has passed validation.
- The current launcher-side code does not by itself implement Minecraft's in-game chunk scheduling or mod-level rendering optimizations.

## Next engineering gates

1. Confirm the environment verification logs on a real GL4ES launch with Ultra OFF and ON. The supplied Minecraft 1.8.9 test measured 391 FPS in both modes, so it showed no gain; do not re-enable GL4ES overrides without repeatable evidence.
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


## Required Sodium and VulkanMod support plan

These are explicit project targets, not claims of current universal compatibility.

### Sodium
- Test exact Minecraft version, supported mod loader (Fabric or NeoForge as appropriate for that Sodium release), required dependencies, Java runtime, and selected renderer/backend.
- Confirm startup, Sodium's video-settings UI, world rendering, chunk rebuilds/updates, and stability.
- Treat Android graphics translation paths as experimental until tested on the actual device/backend. Do not assume that a backend meets Sodium's OpenGL feature requirements from its name alone.

### VulkanMod
- Test the exact Minecraft version, Fabric/Quilt loader, VulkanMod release, Java runtime, device ABI, Vulkan driver/API version, and required Vulkan features.
- Verify that Vulkan is actually initialized and used, and that the required LWJGL Vulkan bindings and native libraries are present for the target ABI.
- Do not claim VulkanMod support on an OpenGL-only backend such as GL4ES. If required Vulkan capabilities are missing, report the combination as unsupported rather than silently forcing it.
- Verify startup failure and fallback behavior on devices that do not meet VulkanMod's requirements.

### Shared acceptance gate
- Test each mod independently with URinthUltra OFF and ON for each candidate supported combination.
- Record game startup, visual correctness, frame-time stability, memory use, crashes, Minecraft/mod/backend versions, and device graphics capabilities.
- A successful Android APK build or one successful launch is not sufficient evidence. Mark only tested combinations as supported; label the rest experimental or unsupported.
- Never change the player's resolution or edit `options.txt` as a compatibility workaround.
