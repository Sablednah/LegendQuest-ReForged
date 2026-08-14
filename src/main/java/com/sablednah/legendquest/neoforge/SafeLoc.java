package com.sablednah.legendquest.neoforge;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;

/**
 * The old SafeLoc, rebuilt: find somewhere near a target where a teleport
 * won't bury, drown-in-lava, or drop the traveller. Scans outward from the
 * target Y for two passable blocks over a solid floor with no lava bath.
 */
public final class SafeLoc {

    public static Optional<BlockPos> find(ServerLevel level, BlockPos near) {
        for (int offset = 0; offset <= 16; offset++) {
            // Alternate up/down so the closest safe layer wins.
            for (int sign : offset == 0 ? new int[] {0} : new int[] {1, -1}) {
                BlockPos feet = near.above(offset * sign);
                if (isSafe(level, feet)) return Optional.of(feet);
            }
        }
        return Optional.empty();
    }

    private static boolean isSafe(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        BlockPos head = feet.above();
        if (!level.getBlockState(below).isFaceSturdy(level, below,
                net.minecraft.core.Direction.UP)) {
            return false; // no floor
        }
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                || !level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
            return false; // buried
        }
        if (level.getFluidState(feet).is(FluidTags.LAVA)
                || level.getFluidState(below).is(FluidTags.LAVA)) {
            return false; // the classic SafeLoc failure mode
        }
        return true;
    }

    private SafeLoc() {}
}
