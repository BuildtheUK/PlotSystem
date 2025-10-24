package net.bteuk.plotsystem.utils.plugins;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import net.bteuk.network.api.CoordinateAPI;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.papercore.LocationAdapter;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.PlotHologram;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

/*
This class adds the implementation of plot creation using worldguard.
 */
public class WGCreatePlot {

    protected final PlotAPI plotAPI;

    private final PlotHelper plotHelper;

    private final CoordinateAPI coordinateAPI;

    public int plotID;

    // Create a new instance of plots.
    public WGCreatePlot(PlotAPI plotAPI, PlotHelper plotHelper, CoordinateAPI coordinateAPI) {
        this.plotAPI = plotAPI;
        this.plotHelper = plotHelper;
        this.coordinateAPI = coordinateAPI;
    }

    // Create a plot with the current selection.
    public boolean createPlot(Player p, World world, String location, List<BlockVector2> vector, int size, int difficulty) {

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
            coordinate_id = coordinateAPI.addCoordinate(LocationAdapter.adapt(l));
        } else {
            p.sendMessage(ChatUtils.error("Unable to add plot marker since you are not in the plot."));
            p.sendMessage(ChatUtils.error("To set the marker, go to the plot and run /ps movemarker " + plotID));
        }

        // Create an entry in the database for the plot.
        plotID = plotAPI.createPlot(size, difficulty, location, coordinate_id);

        // Load the hologram for this plot.
        plotHelper.addPlotHologram(new PlotHologram(plotID));

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
    public boolean createZone(Player p, World world, String location, List<BlockVector2> vector, long expiration, boolean isPublic) {

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
        plotID = plotAPI.createZone(location, expiration, isPublic);

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
