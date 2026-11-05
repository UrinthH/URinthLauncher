package dev.urinth.sunflare.mixin;

import dev.urinth.sunflare.LeafCull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium decides in BlockOcclusionCache.shouldDrawSide whether a block face is drawn while it builds a chunk. For a leaf block this asks
 * {@link LeafCull} whether the face is buried under other leaves, and if so the face is not drawn. The target is looked up by name, so
 * the game still starts (just without this saving) if Sodium is missing or has changed that method.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache")
public abstract class SodiumLeafCullMixin {
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void urinth$skipBuriedLeafFaces(BlockState self, BlockGetter view, BlockPos pos, Direction facing,
                                            CallbackInfoReturnable<Boolean> cir) {
        int depth = LeafCull.depth();
        if (depth <= 0 || !(self.getBlock() instanceof LeavesBlock)) return;
        BlockPos.MutableBlockPos p = LeafCull.scratch();
        if (LeafCull.hidden(depth, n -> {
            p.set(pos.getX() + facing.getStepX() * n, pos.getY() + facing.getStepY() * n, pos.getZ() + facing.getStepZ() * n);
            return view.getBlockState(p).getBlock() instanceof LeavesBlock;
        })) {
            cir.setReturnValue(false);
        }
    }
}
