package net.bteuk.plotsystem.utils.plugins;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import net.bteuk.network.Network;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.PlotHologram;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

import static net.bteuk.network.utils.Constants.MAX_Y;
import static net.bteuk.network.utils.Constants.MIN_Y;

/*
This class adds the implementation of plot creation using worldguard.
 */
public class WGCreatePlot {

    public int plotID;

    // Create a new instance of plots.
    public WGCreatePlot() {
    }

    // Create a plot with the current selection.
    public boolean createPlot(Player p, World world, String location, List<BlockVector2> vector, PlotSQL plotSQL, int size, int difficulty) {

        // Get instance of WorldGuard.
        WorldGuard wg = WorldGuard.getInstance();

        // Get regions.
        RegionManager regions = wg.getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));

        // Checking if regions isn't null, would indicate that the world doesn't exist.
        if (regions == null) {
            return false;
        }

        // Create region to test.
        ProtectedPolygonalRegion region = new ProtectedPolygonalRegion("test", vector, MIN_Y, (MAX_Y - 1));

        // Check whether the region overlaps an existing plot, if true stop the process.
        ApplicableRegionSet set = regions.getApplicableRegions(region);
        if (set.size() > 0) {

            p.sendMessage(ChatUtils.error("Your selection overlaps with an existing plot or zone."));
            return false;

        }

        // Create a coordinate id for the current player location if in the plot.
        int coordinate_id = 0;
        if (region.contains(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ())) {
            Location l = p.getLocation().clone();
            l.setY(l.getY() + 2); /* Increase the y elevation by 2 so the hologram is not at the player's feet */
            coordinate_id = Network.getInstance().getGlobalSQL().addCoordinate(l);
        } else {
            p.sendMessage(ChatUtils.error("Unable to add plot marker since you are not in the plot."));
            p.sendMessage(ChatUtils.error("To set the marker, go to the plot and run /ps movemarker " + plotID));
        }

        // Create an entry in the database for the plot.
        plotID = plotSQL.createPlot(size, difficulty, location, coordinate_id);

        // Load the hologram for this plot.
        PlotHelper.addPlotHologram(new PlotHologram(plotID));

        // Create the region with valid name.
        region = new ProtectedPolygonalRegion(String.valueOf(plotID), vector, MIN_Y, (MAX_Y - 1));

        // Add the regions to the world
        regions.addRegion(region);

        // Save the new region
        try {
            regions.save();
        } catch (
                StorageException e1) {
            e1.printStackTrace();
        }

        return true;
    }

    // Create a zone with the current selection.
    public boolean createZone(Player p, World world, String location, List<BlockVector2> vector, PlotSQL plotSQL, long expiration, boolean is_public) {

        // Get instance of WorldGuard.
        WorldGuard wg = WorldGuard.getInstance();

        // Get regions.
        RegionManager regions = wg.getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));

        // Checking if regions isn't null, would indicate that the world doesn't exist.
        if (regions == null) {
            return false;
        }

        // Create region to test.
        ProtectedPolygonalRegion region = new ProtectedPolygonalRegion("test", vector, MIN_Y, (MAX_Y - 1));

        // Check whether the region overlaps an existing plot, if true stop the process.
        ApplicableRegionSet set = regions.getApplicableRegions(region);
        if (set.size() > 0) {

            p.sendMessage(ChatUtils.error("Your selection overlaps with an existing plot or zone."));
            return false;

        }

        // Create an entry in the database for the plot.
        plotID = plotSQL.createZone(location, expiration, is_public);

        // Create the region with valid name.
        region = new ProtectedPolygonalRegion("z" + plotID, vector, MIN_Y, (MAX_Y - 1));

        // Add the owner to the region.
        region.getMembers().addPlayer(p.getUniqueId());

        // Add the regions to the world
        regions.addRegion(region);

        // Save the new region
        try {
            regions.save();
        } catch (
                StorageException e1) {
            e1.printStackTrace();
        }

        return true;
    }
}
