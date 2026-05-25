package net.bteuk.plotsystem.gui;

import org.btuk.minecraft.gui.Gui;
import org.btuk.minecraft.gui.GuiManager;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.plotsystem.PlotStatus;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.PlotValues;
import net.bteuk.plotsystem.utils.User;
import net.bteuk.plotsystem.utils.Utils;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class ClaimGui extends Gui {

    private final User user;

    private final int plot;

    private final PlotAPI plotAPI;

    private final PlotHelper plotHelper;

    public ClaimGui(User user, int plot, GuiManager guiManager, PlotAPI plotAPI, PlotHelper plotHelper) {
        super(guiManager, 27, ChatUtils.title("Claim Plot"));

        this.user = user;
        this.plot = plot;
        this.plotAPI = plotAPI;
        this.plotHelper = plotHelper;

        createGui();
    }

    private void createGui() {

        int size = plotAPI.getPlotSize(plot);
        setItem(20, Utils.createItem(PlotValues.sizeMaterial(size), 1,
                ChatUtils.title("Plot Size"),
                ChatUtils.line(PlotValues.sizeName(size))));

        int difficulty = plotAPI.getPlotDifficulty(plot);
        setItem(24, Utils.createItem(PlotValues.difficultyMaterial(difficulty), 1,
                ChatUtils.title("Plot Difficulty"),
                ChatUtils.line(PlotValues.difficultyName(difficulty))));

        setItem(22, Utils.createItem(Material.ENDER_EYE, 1,
                        ChatUtils.title("View Plot in Google Maps"),
                        ChatUtils.line("Click to open a link to this plot in google maps.")),
                clickEvent -> {
                    Player player = (Player) clickEvent.getWhoClicked();

                    player.closeInventory();

                    // Get corners of the plot.
                    int[][] corners = plotAPI.getPlotCorners(plot);

                    int sumX = 0;
                    int sumZ = 0;

                    // Find the centre.
                    for (int[] corner : corners) {

                        sumX += corner[0];
                        sumZ += corner[1];

                    }

                    double x = sumX / (double) corners.length;
                    double z = sumZ / (double) corners.length;

                    // Convert to irl coordinates.
                    try {
                        final EarthGeneratorSettings bteGeneratorSettings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);
                        double[] coords = bteGeneratorSettings.projection().toGeo(x, z);

                        // Generate link to google maps.
                        Component message = ChatUtils.success("Click here to open the plot in Google Maps.");
                        message = message.clickEvent(
                                ClickEvent.openUrl("https://www.google.com/maps/@?api=1&map_action=map&basemap=satellite&zoom=21&center=" + coords[1] + "," + coords[0]));
                        player.sendMessage(message);

                    } catch (OutOfProjectionBoundsException e) {
                        e.printStackTrace();
                    }

                });

        setItem(4, Utils.createItem(Material.EMERALD, 1,
                        ChatUtils.title("Claim Plot"),
                        ChatUtils.line("Click to claim the plot and start building.")),
                event -> {
                    if (event.getWhoClicked() instanceof Player player) {
                        player.closeInventory();

                        // Check if the plot is not already claimed, since it may happen that the gui is spammed.
                        PlotStatus plotStatus = plotAPI.getPlotStatus(plot);
                        if (plotStatus == PlotStatus.UNCLAIMED) {

                            // If the plot status can be updated, add the player as plot owner.
                            if (plotHelper.updatePlotStatus(plot, PlotStatus.CLAIMED)) {

                                // If the player can't be given owner, set the plot status back to unclaimed.
                                if (plotAPI.createPlotOwner(plot, user.uuid)) {

                                    // Add player to the worldguard region.
                                    try {
                                        if (WorldGuardFunctions.addMember(String.valueOf(plot), player.getUniqueId().toString(), player.getWorld())) {

                                            player.sendMessage(ChatUtils.success("Successfully claimed plot ")
                                                    .append(Component.text(plot, NamedTextColor.DARK_AQUA))
                                                    .append(ChatUtils.success(", good luck building.")));
                                            // Send link to plot in Google Maps.
                                            player.performCommand("ll");
                                            LOGGER.info("Plot " + plot + " successfully claimed.");

                                        } else {

                                            player.sendMessage(ChatUtils.error("An error occurred while claiming the plot."));
                                            LOGGER.warning("Plot " + plot + " was claimed but they were not added to the worldguard region.");

                                        }
                                    } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
                                        player.sendMessage(ChatUtils.error("An error occurred while claiming the plot, please notify an admin."));
                                        e.printStackTrace();
                                    }

                                } else {

                                    player.sendMessage(ChatUtils.error("An error occurred while claiming the plot."));
                                    LOGGER.warning("Plot owner insert failed for plot " + plot);

                                    // Attempt to set plot back to unclaimed
                                    if (plotHelper.updatePlotStatus(plot, PlotStatus.UNCLAIMED)) {

                                        LOGGER.warning("Plot " + plot + " has been set back to unclaimed.");

                                    } else {

                                        LOGGER.severe("Plot " + plot + " is set to claimed but has no owner!");

                                    }
                                }

                            } else {

                                player.sendMessage(ChatUtils.error("An error occurred while claiming the plot."));
                                LOGGER.warning("Status could not be changed to claimed for plot " + plot);

                            }
                        } else {

                            player.sendMessage(ChatUtils.error("This plot is already claimed, it could be due to clicking the claim button multiple times."));

                        }
                    }
                });
    }
}
