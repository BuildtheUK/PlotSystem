package net.bteuk.plotsystem.gui;

import net.bteuk.network.gui.Gui;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.utils.Time;
import net.bteuk.network.utils.Utils;
import net.bteuk.network.utils.enums.PlotStatus;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.PlotValues;
import net.bteuk.plotsystem.utils.User;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class ClaimGui extends Gui {

    private final User user;

    private final int plot;

    public ClaimGui(User user, int plot) {

        super(27, Component.text("Claim Plot", NamedTextColor.AQUA, TextDecoration.BOLD));

        this.user = user;
        this.plot = plot;

        createGui();

    }

    private void createGui() {

        setItem(20, Utils.createItem(PlotValues.sizeMaterial(user.plotSQL.getInt("SELECT size FROM plot_data WHERE id=" + plot + ";")), 1,
                ChatUtils.title("Plot Size"),
                ChatUtils.line(PlotValues.sizeName(user.plotSQL.getInt("SELECT size FROM plot_data WHERE id=" + plot + ";")))));

        setItem(24, Utils.createItem(PlotValues.difficultyMaterial(user.plotSQL.getInt("SELECT difficulty FROM plot_data WHERE id=" + plot + ";")), 1,
                ChatUtils.title("Plot Difficulty"),
                ChatUtils.line(PlotValues.difficultyName(user.plotSQL.getInt("SELECT difficulty FROM plot_data WHERE id=" + plot + ";")))));

        setItem(22, Utils.createItem(Material.ENDER_EYE, 1,
                        ChatUtils.title("View Plot in Google Maps"),
                        ChatUtils.line("Click to open a link to this plot in google maps.")),
                u ->

                {

                    u.player.closeInventory();

                    // Get corners of the plot.
                    int[][] corners = user.plotSQL.getPlotCorners(plot);

                    int sumX = 0;
                    int sumZ = 0;

                    // Find the centre.
                    for (int[] corner : corners) {

                        sumX += corner[0];
                        sumZ += corner[1];

                    }

                    double x = sumX / (double) corners.length;
                    double z = sumZ / (double) corners.length;

                    // Subtract the coordinate transform to make the coordinates in the real location.
                    x -= user.plotSQL.getInt("SELECT xTransform FROM location_data WHERE name='" +
                            user.plotSQL.getString("SELECT location FROM plot_data WHERE id=" + plot + ";") + "';");
                    z -= user.plotSQL.getInt("SELECT zTransform FROM location_data WHERE name='" +
                            user.plotSQL.getString("SELECT location FROM plot_data WHERE id=" + plot + ";") + "';");

                    // Convert to irl coordinates.

                    try {

                        final EarthGeneratorSettings bteGeneratorSettings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);
                        double[] coords = bteGeneratorSettings.projection().toGeo(x, z);

                        // Generate link to google maps.
                        Component message = ChatUtils.success("Click here to open the plot in Google Maps.");
                        message = message.clickEvent(ClickEvent.clickEvent(ClickEvent.Action.OPEN_URL,
                                "https://www.google.com/maps/@?api=1&map_action=map&basemap=satellite&zoom=21&center=" + coords[1] + "," + coords[0]));
                        u.player.sendMessage(message);

                    } catch (OutOfProjectionBoundsException e) {
                        e.printStackTrace();
                    }

                });

        setItem(4, Utils.createItem(Material.EMERALD, 1,
                        ChatUtils.title("Claim Plot"),
                        ChatUtils.line("Click to claim the plot and start building.")),
                u ->

                {

                    User eUser = PlotSystem.getInstance().getUser(u.player);
                    u.player.closeInventory();

                    // Check if the plot is not already claimed, since it may happen that the gui is spammed.
                    if (eUser.plotSQL.hasRow("SELECT id FROM plot_data WHERE id=" + plot + " AND status='unclaimed';")) {

                        // If the plot status can be updated, add the player as plot owner.
                        if (PlotHelper.updatePlotStatus(plot, PlotStatus.CLAIMED)) {

                            // If the player can't be given owner, set the plot status back to unclaimed.
                            if (eUser.plotSQL.update(
                                    "INSERT INTO plot_members(id,uuid,is_owner,last_enter) VALUES(" + plot + ",'" + eUser.uuid + "',1," + Time.currentTime() + ");")) {

                                // Add player to worldguard region.
                                try {
                                    if (WorldGuardFunctions.addMember(String.valueOf(plot), eUser.uuid, eUser.player.getWorld())) {

                                        eUser.player.sendMessage(ChatUtils.success("Successfully claimed plot ")
                                                .append(Component.text(plot, NamedTextColor.DARK_AQUA))
                                                .append(ChatUtils.success(", good luck building.")));
                                        // Send link to plot in Google Maps.
                                        eUser.player.performCommand("ll");
                                        LOGGER.info("Plot " + plot + " successfully claimed by " + eUser.name);

                                    } else {

                                        eUser.player.sendMessage(ChatUtils.error("An error occurred while claiming the plot."));
                                        LOGGER.warning("Plot " + plot + " was claimed but they were not added to the worldguard region.");

                                    }
                                } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
                                    eUser.player.sendMessage(ChatUtils.error("An error occurred while claiming the plot, please notify an admin."));
                                    e.printStackTrace();
                                }

                            } else {

                                eUser.player.sendMessage(ChatUtils.error("An error occurred while claiming the plot."));
                                LOGGER.warning("Plot owner insert failed for plot " + plot);

                                // Attempt to set plot back to unclaimed
                                if (PlotHelper.updatePlotStatus(plot, PlotStatus.UNCLAIMED)) {

                                    LOGGER.warning("Plot " + plot + " has been set back to unclaimed.");

                                } else {

                                    LOGGER.severe("Plot " + plot + " is set to claimed but has no owner!");

                                }
                            }

                        } else {

                            eUser.player.sendMessage(ChatUtils.error("An error occurred while claiming the plot."));
                            LOGGER.warning("Status could not be changed to claimed for plot " + plot);

                        }
                    } else {

                        eUser.player.sendMessage(ChatUtils.error("This plot is already claimed, it could be due to clicking the claim button multiple times."));

                    }

                });
    }

    public void refresh() {

        clearGui();
        createGui();

    }
}
