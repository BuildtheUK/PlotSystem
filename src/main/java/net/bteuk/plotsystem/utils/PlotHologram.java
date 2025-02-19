package net.bteuk.plotsystem.utils;

import eu.decentsoftware.holograms.api.holograms.Hologram;
import lombok.Getter;
import net.bteuk.network.Network;
import net.bteuk.network.utils.Holograms;
import net.bteuk.network.utils.enums.PlotStatus;
import net.bteuk.network.utils.enums.SubmittedStatus;
import net.bteuk.plotsystem.PlotSystem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A plot hologram is a marker located in the plot that allows to player to view info about the plot, or claim it, if unclaimed.
 */
public class PlotHologram {

    @Getter
    private final int plot;

    private PlotStatus plotStatus;

    private SubmittedStatus submittedStatus;

    private Location location;

    private final HashMap<PlotHologramType, Hologram> holograms = new HashMap<>();

    public PlotHologram(int plot) {
        this.plot = plot;
        createHologram();
    }

    /**
     * Set the status of the plot, the status determines the info displayed on the hologram.
     * Updates the hologram as a result.
     */
    public void updatePlotStatus(PlotStatus plotStatus, SubmittedStatus submittedStatus) {
        this.plotStatus = plotStatus;
        this.submittedStatus = submittedStatus;
        updateHologram();
    }

    /**
     * Update the hologram locations by removing and adding them back.
     */
    public void updateLocation() {
        updateHologram();
    }

    /**
     * Set the visibility of this hologram for all players.
     */
    public void setHologramVisibility() {
        // Set default visibility to false.
        holograms.values().forEach(hologram -> {
            if (hologram != null) {
                hologram.setDefaultVisibleState(false);
            }
        });
        Bukkit.getOnlinePlayers().forEach(this::setHologramVisibilityForPlayer);
    }

    /**
     * Set the visibility of this hologram for a specific player.
     *
     * @param p the {@link Player} to add
     */
    public void setHologramVisibilityForPlayer(Player p) {
        // Determine which type to show for this player, default is ALL.
        PlotHologramType showType = PlotHologramType.ALL;
        if (plotStatus == PlotStatus.CLAIMED || plotStatus == PlotStatus.SUBMITTED) {
            // Check if the player is the plot owner.
            if (Network.getInstance().getPlotSQL().hasRow("SELECT id FROM plot_members WHERE id=" + plot + " AND uuid='" + p.getUniqueId() + "' AND is_owner=1;")) {
                showType = PlotHologramType.OWNER;
            } else if (Network.getInstance().getPlotSQL().hasRow("SELECT id FROM plot_members WHERE id=" + plot + " AND uuid='" + p.getUniqueId() + "' AND is_owner=0;")) {
                showType = PlotHologramType.MEMBER;
            } else {
                if (plotStatus == PlotStatus.SUBMITTED) {
                    switch (submittedStatus) {
                        case SUBMITTED -> {
                            if (Network.getInstance().getPlotSQL().canReviewPlot(plot, p.getUniqueId().toString(), p.hasPermission("group.architect"), p.hasPermission("group.reviewer"))) {
                                showType = PlotHologramType.REVIEWER;
                            }
                        }

                        case UNDER_REVIEW -> {
                            User u = PlotSystem.getInstance().getUser(p);
                            if (u != null && u.getReview() != null &&  u.getReview().getPlotID() == plot) {
                                showType = PlotHologramType.REVIEWER;
                            }
                        }

                        case AWAITING_VERIFICATION -> //TODO
                        case UNDER_VERIFICATION -> //TODO
                    }
                }
            }
        }
        for (Map.Entry<PlotHologramType, Hologram> entry : holograms.entrySet()) {
            if (entry.getValue() != null) {
                if (entry.getKey() == showType) {
                    entry.getValue().setShowPlayer(p);
                } else {
                    entry.getValue().removeShowPlayer(p);
                }
            }
        }
    }

    /**
     * Check if no holograms exist.
     *
     * @return true, if no holograms exist
     */
    public boolean isEmpty() {
        return holograms.isEmpty();
    }

    /**
     * Create the hologram for the plot.
     */
    private void createHologram() {
        // Get the plot status.
        plotStatus = PlotStatus.fromDatabaseValue(Network.getInstance().getPlotSQL().getString("SELECT status FROM plot_data WHERE id=" + plot));

        // Get the location of the hologram.
        int coordinate = Network.getInstance().getPlotSQL().getInt("SELECT coordinate_id FROM plot_data WHERE id=" + plot);
        if (coordinate != 0) {
            location = Network.getInstance().getGlobalSQL().getLocation(coordinate);
        }

        // Create the holograms, depending on the status, multiple holograms may be necessary for specific players.
        createHolograms();

        // Set the hologram visibility.
        setHologramVisibility();
    }

    private void createHolograms() {
        if (plotStatus != null && location != null) {
            if (plotStatus == PlotStatus.UNCLAIMED) {
                // Create a hologram, visible for all players.
                holograms.put(PlotHologramType.ALL, createUnclaimedHologram());
            } else if (plotStatus == PlotStatus.CLAIMED || plotStatus == PlotStatus.SUBMITTED) {
                // Create a hologram for the plot owner.
                holograms.put(PlotHologramType.OWNER, createClaimedHologram("&fYou are the owner of the plot", "OWNER"));
                holograms.put(PlotHologramType.MEMBER, createClaimedHologram("&fYou are a member of the plot", "MEMBER"));
                // Create a hologram for the reviewers.
                if (plotStatus == PlotStatus.SUBMITTED) {
                    switch (submittedStatus) {
                        case SUBMITTED ->
                                holograms.put(PlotHologramType.REVIEWER, createClaimedHologram("&fThis plot is submitted", "SUBMITTED"));
                        case UNDER_REVIEW ->
                                holograms.put(PlotHologramType.REVIEWER, createClaimedHologram("&fYou are reviewing this plot", "REVIEWER"));
                        case AWAITING_VERIFICATION -> //TODO
                        case UNDER_VERIFICATION -> //TODO
                    }
                }
                // Create a hologram for the remaining players.
                holograms.put(PlotHologramType.ALL, createClaimedHologram("&fThis plot is claimed", "ALL"));
            }
        }
    }

    private Hologram createUnclaimedHologram() {
        List<String> text = hologramTitle();
        text.add("&fThis plot is unclaimed");
        text.add("&fClick to claim this plot");
        return Holograms.createHologram(plot + "_UNCLAIMED", location, text);
    }

    private Hologram createClaimedHologram(String line2, String type) {
        List<String> text = hologramTitle();
        text.add(line2);
        text.add("&fClick to open the plot info");
        return Holograms.createHologram(plot + "_" + type, location, text);
    }

    private List<String> hologramTitle() {
        List<String> text = new ArrayList<>();
        text.add("&b&lPlot " + plot);
        return text;
    }

    /**
     * Update the information displayed on the hologram.
     */
    private void updateHologram() {
        // Remove existing holograms.
        holograms.values().forEach(Hologram::delete);
        holograms.clear();
        createHologram();
    }

    private enum PlotHologramType {
        ALL,
        OWNER,
        MEMBER,
        REVIEWER
    }
}