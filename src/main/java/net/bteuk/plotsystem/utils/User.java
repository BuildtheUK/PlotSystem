package net.bteuk.plotsystem.utils;

import lombok.Getter;
import lombok.Setter;
import net.bteuk.network.sql.GlobalSQL;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.gui.ClaimGui;
import net.bteuk.plotsystem.gui.CreatePlotGui;
import net.bteuk.plotsystem.gui.CreateZoneGui;
import net.bteuk.plotsystem.reviewing.ReviewAction;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class User {

    //Basic information about the player.
    public final Player player;
    public final String uuid;
    public final String name;

    public final SelectionTool selectionTool;

    public int inPlot = 0;
    public int inZone = 0;

    @Getter
    @Setter
    private ReviewAction review;

    public final GlobalSQL globalSQL;
    public final PlotSQL plotSQL;

    // Guis
    public ClaimGui claimGui;
    public CreatePlotGui createPlotGui;
    public CreateZoneGui createZoneGui;

    // Store the location of the player on interval, this allows the server to check when to update the outlines.
    public Location lastLocation;

    // Skip outlines for these plots.
    @Getter
    private final List<String> skipOutlines = new ArrayList<>();

    // Disable outlines for all plots and zones.
    @Getter
    @Setter
    private boolean disableOutlines;

    public User(Player player, GlobalSQL globalSQL, PlotSQL plotSQL) {

        //Set sql
        this.globalSQL = globalSQL;
        this.plotSQL = plotSQL;

        //Set player, uuid and name variable.
        this.player = player;
        uuid = player.getUniqueId().toString();
        name = player.getName();

        //Set selection tool, only players with the valid roles can use it.
        selectionTool = new SelectionTool(this, plotSQL);

        //Set last location to current location.
        lastLocation = player.getLocation();
        //Set outlines for player.
        PlotSystem.getInstance().getOutlines().addNearbyOutlines(this);

    }
}
