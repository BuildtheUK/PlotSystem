package net.bteuk.plotsystem.utils;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.World;

public record CopyRegionFormat(World copyWorld, World pasteWorld, BlockVector3 minPoint, BlockVector3 maxPoint, BlockVector3 pasteMinPoint) {}
