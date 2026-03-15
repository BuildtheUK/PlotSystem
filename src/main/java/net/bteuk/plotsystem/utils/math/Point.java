package net.bteuk.plotsystem.utils.math;

import com.sk89q.worldedit.math.BlockVector2;

import java.util.List;

public class Point {

    public static BlockVector2 getAveragePoint(List<BlockVector2> points) {

        double size = points.size();
        double x = 0;
        double z = 0;

        for (BlockVector2 bv : points) {

            x += bv.x() / size;
            z += bv.z() / size;

        }

        return (BlockVector2.at(x, z));

    }
}
