package net.bteuk.plotsystem.utils;

import org.bukkit.block.data.BlockData;

// Helper class to close x,z locations of blocks
public record BlockLocation(BlockData block, int x, int z) {

    @Override
    public boolean equals(Object o) {
        if (o instanceof BlockLocation bl) {
            return (this.x == bl.x() && this.z == bl.z());
        }
        return false;
    }
}
