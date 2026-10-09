# URinthRender implementation status

## Current architecture

- **URinthUltra Mode and its wrapper have been removed.** There is no separate Ultra ON/OFF launch profile.
- The player selects the graphics backend directly from **Settings → Video and renderer → Renderer**.
- The launcher uses the selected renderer's own compatibility checks, environment setup, native library, and initialization path. If setup fails, the existing GL4ES fallback remains available.
- External renderer plugin discovery supports the package IDs explicitly listed in the launcher, including compatible FCL/MIO ANGLE, LTW, GL4ES, Mesa, and MobileGlues packages.
- Device thermal monitoring is diagnostic only. It does not select a renderer or change graphics options.
- The launcher must never force the player's resolution or modify `options.txt`.

## Important limitations

- Adding a renderer entry or finding a plugin does not create a new native graphics driver. A true custom URinthRender backend would require its own native implementation and build integration; that is not currently implemented.
- A successful Android build proves compilation and packaging only. It does not prove game startup, rendering correctness, stability, or FPS gains.
- **Sodium compatibility is not guaranteed.** It depends on the exact Minecraft version, mod loader, Sodium version, runtime, selected backend, and exposed graphics capabilities. Validate each combination and mark unsupported combinations clearly.
- **VulkanMod compatibility is separate.** It requires a working Vulkan path, compatible LWJGL Vulkan bindings/native libraries for the device ABI, and the required Vulkan features. An OpenGL-only backend must not be treated as a Vulkan backend.
- Do not claim FPS improvements without repeatable frame-time measurements on the target device. Launcher startup timing and Android view callbacks are not measurements of Minecraft's live render loop.

## Validation checklist

For each test, record device/GPU, Android version, ABI, Minecraft version, mod loader and versions, Java runtime, selected renderer and plugin version, launch result, renderer reported in-game, crashes/logs, and repeatable frame-time results. Keep resolution and player graphics options unchanged during comparisons.
