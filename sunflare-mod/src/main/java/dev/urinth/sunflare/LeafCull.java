package dev.urinth.sunflare;

import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;

/**
 * Cheaper Fancy leaves. Fancy leaves draw every face of every leaf block, including the ones buried deep inside a tree where nobody can
 * see them, and a phone GPU pays for all that overdraw. This skips a leaf face when the next {@code depth} blocks in front of it are
 * leaves too, so the first {@code depth} layers of every tree look exactly as before and only the hidden inner faces go.
 * The check is made while Sodium builds a chunk, so it costs nothing per frame. Plain logic, unit tested.
 */
public final class LeafCull {
    private LeafCull() {}

    public static final int MAX_DEPTH = 3;
    public static final int DEFAULT_DEPTH = 2;
    public static final String[] NAMES = {"Off", "Light (1 layer)", "Balanced (2 layers)", "Safe (3 layers)"};

    private static volatile int depth = DEFAULT_DEPTH;
    /** Chunks are built on several threads, each with its own scratch position (so no garbage is made per face). */
    private static final ThreadLocal<BlockPos.MutableBlockPos> SCRATCH = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    /** A position this thread may change freely. */
    public static BlockPos.MutableBlockPos scratch() {
        return SCRATCH.get();
    }

    /** How many layers of leaves stay visible (0 = nothing is skipped). */
    public static int depth() {
        return depth;
    }

    public static void setDepth(int value) {
        depth = Math.max(0, Math.min(MAX_DEPTH, value));
    }

    /**
     * @param depth     layers that stay visible, 0 = never skip
     * @param leavesAt  whether the block {@code n} steps in front of the face (1 = the neighbour) is a leaf block
     * @return true when the face is hidden under {@code depth} leaf blocks and can be skipped
     */
    public static boolean hidden(int depth, IntPredicate leavesAt) {
        if (depth <= 0) return false;
        for (int n = 1; n <= depth; n++) {
            if (!leavesAt.test(n)) return false;
        }
        return true;
    }
}
