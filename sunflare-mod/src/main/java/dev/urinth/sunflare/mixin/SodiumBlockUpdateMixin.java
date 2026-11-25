package dev.urinth.sunflare.mixin;

import dev.urinth.sunflare.BlockUpdates;
import dev.urinth.sunflare.SunFlareMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * RenderSectionManager.scheduleRebuild is told whether a block was changed by the player ("important") and asks
 * allowImportantRebuilds whether such a rebuild may be done at once. Sodium's default answer is no, for every rebuild. This notes
 * whether the rebuild being scheduled is the player's, and for those only (never for blocks the server, the light or the water
 * changed) answers yes when {@link BlockUpdates} says it may be instant. That is the only call of allowImportantRebuilds, so it
 * cannot reach any other rebuild. Looked up by name: without Sodium, or with one that changed these methods, nothing happens.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager")
public abstract class SodiumBlockUpdateMixin {
    @Inject(method = "scheduleRebuild", at = @At("HEAD"), remap = false, require = 0)
    private void urinth$notePlayerEdit(int x, int y, int z, boolean important, CallbackInfo ci) {
        BlockUpdates.begin(important);
    }

    @Inject(method = "allowImportantRebuilds", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void urinth$allowPlayerEdit(CallbackInfoReturnable<Boolean> cir) {
        if (!BlockUpdates.grant()) return;
        cir.setReturnValue(Boolean.TRUE);
        if (BlockUpdates.firstGrant()) {
            SunFlareMod.note("[BlockUpdates] a block the player changed is rebuilt at once (mode " + BlockUpdates.NAMES[BlockUpdates.mode()] + ")");
        }
    }
}
