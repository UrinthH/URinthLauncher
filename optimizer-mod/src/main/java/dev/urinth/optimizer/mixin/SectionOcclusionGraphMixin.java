package dev.urinth.optimizer.mixin;

import dev.urinth.optimizer.Hooks;
import dev.urinth.optimizer.SoftDistance;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The game finds the chunk sections to draw by walking outwards from the camera as far as the render distance. This makes that
 * walk stop at the distance the mod is drawing: the sections beyond it are not visited, so they are not meshed, not sorted and
 * not drawn. The three places that read the render distance during the walk (the horizontal check, the vertical check and the
 * start of a full update) all go through the same answer. Sections that were already built stay built.
 */
@Mixin(SectionOcclusionGraph.class)
public abstract class SectionOcclusionGraphMixin {
    @Redirect(
        method = {"isInViewDistance", "getRelativeFrom", "initializeQueueForFullUpdate"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ViewArea;getViewDistance()I"),
        require = 0)
    private int urinth$softCull(ViewArea area) {
        int real = area.getViewDistance();
        try {
            Hooks.cullSeen();
            int limit = SoftDistance.cullLimit();
            return limit < real ? limit : real;
        } catch (Throwable t) {
            Hooks.failed("culling", t);
            return real;
        }
    }
}
