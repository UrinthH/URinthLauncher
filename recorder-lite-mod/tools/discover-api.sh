#!/usr/bin/env bash
# Development aid: prints the signatures of the Minecraft, Fabric API and LWJGL members the recorder uses, so the code is written
# against what is really there. It runs in CI after the build and prints into the job log.
set -u
pj() {  # pj <jar> <class> [grep pattern]
  if [ -n "${3:-}" ]; then javap -public -cp "$1" "$2" 2>&1 | grep -E "$3"; else javap -public -cp "$1" "$2" 2>&1; fi
}
JARS=$(find "$HOME/.gradle" . -name '*.jar' 2>/dev/null)
MC=""
for j in $(echo "$JARS" | grep -i -E 'minecraft' | grep -v -E 'sources|javadoc'); do
  if javap -cp "$j" net.minecraft.client.Minecraft >/dev/null 2>&1; then MC="$j"; break; fi
done
echo "MC JAR: $MC"
find_one() { echo "$JARS" | grep -E "$1" | grep -v -E 'sources|javadoc' | head -1; }
GLJ=$(find_one 'lwjgl-opengl-3\.3\.3\.jar'); CORE=$(find_one '/lwjgl-3\.3\.3\.jar'); STB=$(find_one 'lwjgl-stb-3\.3\.3\.jar')
echo "LWJGL: $CORE | $GLJ | $STB"
fab() { find_one "$1"; }
REND=$(fab 'fabric-rendering-v1.*\.jar'); SCR=$(fab 'fabric-screen-api-v1.*\.jar'); KEY=$(fab 'fabric-key-binding-api-v1.*\.jar')
LIFE=$(fab 'fabric-lifecycle-events-v1.*\.jar'); CMD=$(fab 'fabric-command-api-v2.*\.jar')
echo "FABRIC: $REND | $SCR | $KEY | $LIFE | $CMD"

echo "::group::Minecraft"
pj "$MC" net.minecraft.client.Minecraft 'getInstance|getMainRenderTarget|getWindow\(|gameDirectory|screen;|player;|level;|options;|getFps|isPaused|gui;|font;|getDeltaTracker|getToasts|execute\(|isSingleplayer|getConnection|getCurrentServer|runTick|getFrameTimeNs|renderBuffers|setScreen'
echo "-- RenderTarget"; pj "$MC" com.mojang.blaze3d.pipeline.RenderTarget
echo "-- Window"; pj "$MC" com.mojang.blaze3d.platform.Window 'getWidth|getHeight|getGuiScaled|getScreenWidth|getScreenHeight|getGuiScale|getWindow|isFocused|getRefreshRate|getFramerateLimit'
echo "-- GuiGraphics"; pj "$MC" net.minecraft.client.gui.GuiGraphics 'fill\(|drawString|guiWidth|guiHeight|pose\(|flush'
echo "-- Screen"; pj "$MC" net.minecraft.client.gui.screens.Screen 'Screen\(|init\(|render\(|addRenderableWidget|width;|height;|title;|onClose|minecraft;|keyPressed|isPauseScreen|renderBackground|font;|getTitle'
echo "-- Button"; pj "$MC" net.minecraft.client.gui.components.Button
echo "-- Button.Builder"; pj "$MC" 'net.minecraft.client.gui.components.Button$Builder'
echo "-- KeyMapping"; pj "$MC" net.minecraft.client.KeyMapping 'KeyMapping\(|consumeClick|isDown|getName|setKey|same|matches'
echo "-- InputConstants"; pj "$MC" com.mojang.blaze3d.platform.InputConstants 'Type|KEY_MINUS|KEY_EQUALS|KEY_F9|KEY_F8|KEY_R|getKey|isKeyDown|keyCodeText'
echo "-- Component"; pj "$MC" net.minecraft.network.chat.Component 'literal|translatable|empty'
echo "-- DeltaTracker"; pj "$MC" net.minecraft.client.DeltaTracker
echo "-- ChatComponent"; pj "$MC" net.minecraft.client.gui.components.ChatComponent 'addMessage'
echo "-- LocalPlayer"; pj "$MC" net.minecraft.client.player.LocalPlayer 'displayClientMessage|sendSystemMessage'
echo "::endgroup::"

echo "::group::Fabric API"
for c in net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback; do echo "-- $c"; pj "$REND" $c; done
echo "-- ScreenEvents"; pj "$SCR" net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
for c in 'net.fabricmc.fabric.api.client.screen.v1.ScreenEvents$AfterRender' 'net.fabricmc.fabric.api.client.screen.v1.ScreenEvents$AfterInit'; do echo "-- $c"; pj "$SCR" "$c"; done
echo "-- KeyBindingHelper"; pj "$KEY" net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
echo "-- ClientTickEvents"; pj "$LIFE" net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
echo "-- ClientLifecycleEvents"; pj "$LIFE" net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
echo "-- ClientCommandRegistrationCallback"; pj "$CMD" net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
echo "-- ClientCommandManager"; pj "$CMD" net.fabricmc.fabric.api.client.command.v2.ClientCommandManager 'literal|argument'
echo "-- FabricClientCommandSource"; pj "$CMD" net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource 'sendFeedback|sendError|getClient'
echo "::endgroup::"

echo "::group::LWJGL"
echo "-- GL11C"; pj "$GLJ" org.lwjgl.opengl.GL11C 'glReadPixels|glGetInteger\(|glGetBoolean\(|glIsEnabled|glViewport|glColorMask|glEnable|glDisable|glGenTextures|glBindTexture|glTexImage2D|glTexParameteri|glDeleteTextures|glPixelStorei|glFlush|glGetError|glGetIntegerv'
echo "-- GL13C"; pj "$GLJ" org.lwjgl.opengl.GL13C 'glActiveTexture'
echo "-- GL15C"; pj "$GLJ" org.lwjgl.opengl.GL15C 'glGenBuffers|glBindBuffer|glBufferData|glUnmapBuffer|glDeleteBuffers|glMapBuffer\('
echo "-- GL20C"; pj "$GLJ" org.lwjgl.opengl.GL20C 'glCreateShader|glShaderSource|glCompileShader|glGetShaderi|glGetShaderInfoLog|glCreateProgram|glAttachShader|glLinkProgram|glGetProgrami|glGetProgramInfoLog|glUseProgram|glGetUniformLocation|glUniform1i|glUniform2i|glUniform1f|glDeleteShader|glDeleteProgram|glEnableVertexAttribArray|glVertexAttribPointer|glBindAttribLocation|glGetAttribLocation|glDisableVertexAttribArray'
echo "-- GL30C"; pj "$GLJ" org.lwjgl.opengl.GL30C 'glBlitFramebuffer|glBindFramebuffer|glFramebufferTexture2D|glCheckFramebufferStatus|glGenFramebuffers|glDeleteFramebuffers|glMapBufferRange|glGenVertexArrays|glBindVertexArray|glDeleteVertexArrays|glGetIntegeri|glFlushMappedBufferRange'
echo "-- GL32C"; pj "$GLJ" org.lwjgl.opengl.GL32C 'glFenceSync|glClientWaitSync|glDeleteSync|glIsSync'
echo "-- MemoryUtil"; pj "$CORE" org.lwjgl.system.MemoryUtil 'memAddress\(java.nio.ByteBuffer\)|memByteBuffer\(long, int\)|memCopy\(long, long, long\)|memAlloc\(int\)|memFree\(java.nio.Buffer\)'
echo "-- STBImageWrite"; pj "$STB" org.lwjgl.stb.STBImageWrite 'jpg'
echo "-- STBIWriteCallbackI"; pj "$STB" org.lwjgl.stb.STBIWriteCallbackI
echo "-- STBIWriteCallback"; pj "$STB" org.lwjgl.stb.STBIWriteCallback 'create|invoke'
echo "::endgroup::"

echo "::group::Extra members used by the controls"
echo "-- Gui"; pj "$MC" net.minecraft.client.gui.Gui 'getChat|render\('
echo "-- Font"; pj "$MC" net.minecraft.client.gui.Font 'width\('
echo "-- AbstractWidget"; pj "$MC" net.minecraft.client.gui.components.AbstractWidget 'setMessage|getMessage'
echo "-- Screen (protected members too)"; javap -protected -cp "$MC" net.minecraft.client.gui.screens.Screen 2>&1 | grep -E 'addRenderableWidget|Screen\(|Font font|Minecraft minecraft|void init\(\)|clearWidgets|rebuildWidgets'
echo "-- PauseScreen"; pj "$MC" net.minecraft.client.gui.screens.PauseScreen
echo "-- Screens (Fabric)"; pj "$SCR" net.fabricmc.fabric.api.client.screen.v1.Screens
echo "-- FabricLoader"; LOADER=$(find_one 'fabric-loader-[0-9].*\.jar'); echo "$LOADER"; pj "$LOADER" net.fabricmc.loader.api.FabricLoader 'getGameDir|getConfigDir|getInstance'
echo "-- CommandBuildContext"; pj "$MC" net.minecraft.commands.CommandBuildContext
echo "-- Brigadier"; BR=$(find_one 'brigadier-[0-9].*\.jar'); echo "$BR"; pj "$BR" com.mojang.brigadier.suggestion.SuggestionsBuilder 'suggest\(java.lang.String\)|buildFuture'
echo "-- STBIWriteCallback (all)"; pj "$STB" org.lwjgl.stb.STBIWriteCallback
echo "-- Callback.free"; pj "$CORE" org.lwjgl.system.Callback 'free|Callback\('
echo "::endgroup::"

echo "::group::Where HudRenderCallback fires (is it skipped when the HUD is hidden with F1?)"
for j in $(echo "$JARS" | grep -E 'fabric-rendering-v1.*\.jar' | grep -v -E 'sources|javadoc'); do
  echo "-- jar $j"
  for c in $(unzip -Z1 "$j" 2>/dev/null | grep -E 'mixin/client/rendering/.*(Gui|Hud).*Mixin.*\.class$' | sed 's/\.class$//; s#/#.#g'); do
    echo "   class $c"
    javap -v -p -cp "$j" "$c" 2>&1 | grep -E 'method=|target=|Lorg/spongepowered/asm/mixin/injection/(Inject|At)|ordinal|HudRenderCallback|value = |at=' | head -40
  done
done
echo "-- vanilla Gui.render(...) bytecode (only the interesting instructions)"
javap -c -p -cp "$MC" net.minecraft.client.gui.Gui 2>&1 | awk '/ void render\(/{p=1} p{print} /^$/{if(p)exit}' | grep -E 'void render|hideGui|invokevirtual|invokestatic|invokeinterface|ifne|ifeq|return' | head -80
echo "-- vanilla GameRenderer.render(...) bytecode (only the interesting instructions)"
javap -c -p -cp "$MC" net.minecraft.client.renderer.GameRenderer 2>&1 | awk '/ void render\(/{p=1} p{print} /^$/{if(p)exit}' | grep -E 'void render|hideGui|Gui\.render|renderWithTooltip|flush|renderLevel|blit|ifne|ifeq|return|Overlay' | head -80
echo "::endgroup::"
