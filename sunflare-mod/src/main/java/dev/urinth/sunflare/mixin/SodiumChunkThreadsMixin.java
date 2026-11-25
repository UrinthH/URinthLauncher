package dev.urinth.sunflare.mixin;

import dev.urinth.sunflare.ChunkThreads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium's ChunkBuilder asks getThreadCount how many helper threads to start (once, when the world renderer is made; it logs the
 * result as "Started N worker threads"). A fixed number chosen on the URinth page replaces the answer. Auto only raises what Sodium
 * itself would pick when the player has not chosen a number in Sodium's own settings (getOptimalThreadCount is only asked then), so a
 * number set in Sodium always stays as it is. Looked up by name: without Sodium, or with one that changed these methods, nothing happens.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder")
public abstract class SodiumChunkThreadsMixin {
    @Inject(method = "getThreadCount", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void urinth$fixedHelpers(CallbackInfoReturnable<Integer> cir) {
        int fixed = ChunkThreads.fixedCount(ChunkThreads.setting(), Runtime.getRuntime().availableProcessors());
        if (fixed > 0) cir.setReturnValue(fixed);
    }

    @Inject(method = "getOptimalThreadCount", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void urinth$autoHelpers(CallbackInfoReturnable<Integer> cir) {
        if (ChunkThreads.setting() != ChunkThreads.AUTO) return;
        int sodiums = cir.getReturnValue();
        int count = ChunkThreads.autoCount(sodiums, Runtime.getRuntime().availableProcessors());
        if (count != sodiums) cir.setReturnValue(count);
    }
}
