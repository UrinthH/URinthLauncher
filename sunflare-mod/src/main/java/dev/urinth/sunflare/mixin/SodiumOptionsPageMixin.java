package dev.urinth.sunflare.mixin;

import dev.urinth.sunflare.UrinthPageHook;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium's video settings screen builds its list of pages in its constructor, and add-on mods (Sodium Extra, MoreCulling) put their
 * own pages at the end of that list from the end of the constructor. This does the same for the URinth page. Reese's Sodium Options
 * draws the same list, so the page shows up there too. The target is looked up by name and the mixin is not required, so the game
 * still starts, with the separate buttons instead of the page, if Sodium is missing or has changed.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI", remap = false)
public abstract class SodiumOptionsPageMixin {
    @Shadow
    @Final
    private List<Object> pages;

    // The handler lists the constructor's own argument (the screen that opened the settings), as the target requires.
    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
    private void urinth$addPage(Screen prevScreen, CallbackInfo ci) {
        UrinthPageHook.addTo(this.pages);
    }
}
