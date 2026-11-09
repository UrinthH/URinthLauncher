package dev.urinth.optimizer.mixin;

import dev.urinth.optimizer.Hooks;
import dev.urinth.optimizer.UploadGate;
import java.util.Queue;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Finished chunk meshes wait in a queue and the game copies all of them to the graphics card in one go every frame. After a big
 * batch that is one very long frame. This copies them for a few milliseconds per frame and leaves the rest for the next frames
 * (the queue keeps its order). If nothing is waiting, or a lot is (the world is loading), the game's own loop runs unchanged.
 */
@Mixin(SectionRenderDispatcher.class)
public abstract class SectionRenderDispatcherMixin {
    @Shadow
    @Final
    private Queue<Runnable> toUpload;

    @Inject(method = "uploadAllPendingUploads", at = @At("HEAD"), cancellable = true)
    private void urinth$budgetedUploads(CallbackInfo ci) {
        if (!Hooks.usable("uploads")) return;
        long budget;
        try {
            Hooks.uploadsSeen();
            budget = UploadGate.nanos(this.toUpload.size());
        } catch (Throwable t) {
            Hooks.failed("uploads", t);
            return;
        }
        if (budget <= 0L) return;
        long deadline = System.nanoTime() + budget;
        Runnable task;
        // at least one mesh per frame, so the queue always moves; an error in a task is the game's own and goes up as usual
        while ((task = this.toUpload.poll()) != null) {
            task.run();
            if (System.nanoTime() >= deadline) break;
        }
        ci.cancel();
    }
}
