package net.bteuk.plotsystem.reviewing;

import com.sk89q.worldedit.math.BlockVector2;
import net.bteuk.network.Network;
import net.bteuk.network.gui.Gui;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.GlobalSQL;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.Utils;
import net.bteuk.network.utils.enums.SubmittedStatus;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.exceptions.WorldNotFoundException;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;

import static net.bteuk.plotsystem.utils.PlotValues.difficultyMaterial;

public class ReviewGui extends Gui {

    private final Review review;

    private final GlobalSQL globalSQL;
    private final PlotSQL plotSQL;
    private final World world;

    public ReviewGui(Review review) {
        super(27, Component.text("Review Menu", NamedTextColor.AQUA, TextDecoration.BOLD));

        this.review = review;

        globalSQL = Network.getInstance().getGlobalSQL();
        plotSQL = Network.getInstance().getPlotSQL();

        //Get world of plot.
        world = Bukkit.getWorld(plotSQL.getString("SELECT location FROM plot_data WHERE id=" + review.getPlotID() + ";"));

        createGui();

    }

    private void createGui() {

        setItem(4, Utils.createItem(Material.BOOK, 1,
                ChatUtils.title("Plot Info"),
                ChatUtils.line("Plot ID: " + review.getPlotID()),
                ChatUtils.line("Plot Owner: " + globalSQL.getString("SELECT name FROM player_data WHERE uuid='" + review.getPlotOwner() + "';"))));

        setItem(12, Utils.createItem(Material.GRASS_BLOCK, 1,
                        ChatUtils.title("Before View"),
                        ChatUtils.line("Teleport to the plot before it was claimed.")),
                u -> {

                    //Teleport to plot in original state.
                    u.player.closeInventory();

                    try {
                        Location l = WorldGuardFunctions.getBeforeLocation(String.valueOf(review.getPlotID()), world);
                        u.player.teleport(l);
                    } catch (RegionManagerNotFoundException | RegionNotFoundException | WorldNotFoundException e) {
                        u.player.sendMessage(ChatUtils.error("Unable to teleport you to the before view of this plot, please contact an admin."));
                        e.printStackTrace();
                    }

                    //Try to create the outline of the before view.
                    try {

                        //Get outlines of the plot.
                        List<BlockVector2> vector = WorldGuardFunctions.getPointsTransformedToSaveWorld(String.valueOf(review.getPlotID()), world);

                        //Get the plot difficulty.
                        int difficulty = plotSQL.getInt("SELECT difficulty FROM plot_data WHERE id=" + review.getPlotID() + ";");

                        //Draw the outline.
                        PlotSystem.getInstance().getOutlines().addOutline(u.player, vector, difficultyMaterial(difficulty).createBlockData());

                    } catch (RegionNotFoundException | RegionManagerNotFoundException e) {

                        u.player.sendMessage(Component.text("Outline could not be drawn in save world, please contact an admin!", NamedTextColor.DARK_RED));
                        e.printStackTrace();

                    }

                });

        setItem(14, Utils.createItem(Material.STONE_BRICKS, 1,
                        ChatUtils.title("Current View"),
                        ChatUtils.line("Teleport to the current view of the plot.")),
                u -> {

                    //Teleport to plot in current state.
                    u.player.closeInventory();

                    try {
                        Location l = WorldGuardFunctions.getCurrentLocation(String.valueOf(review.getPlotID()), world);
                        u.player.teleport(l);
                    } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
                        u.player.sendMessage(ChatUtils.error("Unable to teleport you to the this plot, please contact an admin."));
                        e.printStackTrace();
                    }

                });

        setItem(10, Utils.createItem(Material.LIME_CONCRETE, 1,
                        ChatUtils.title("Accept Plot"),
                        ChatUtils.line("Accept the plot.")),
                u -> {
                    // TODO: Check if the plot can be accepted with the current feedback settings.

                    // Accept the plot.
                    review.save(true);

                    u.player.closeInventory();
                });

        setItem(16, Utils.createItem(Material.RED_CONCRETE, 1,
                        ChatUtils.title("Deny Plot"),
                        ChatUtils.line("Deny the plot and return it to the plot owner.")),
                u -> {

                    // TODO: Check if the plot has feedback for all categories that are not sufficient.

                    review.save(false);

                    u.player.closeInventory();

                });

        //View previous feedback, if it exists.
        if (plotSQL.hasRow("SELECT id FROM deny_data WHERE uuid='" + review.getPlotOwner() + "' AND id=" + review.getPlotID() + ";")) {

            setItem(18, Utils.createItem(Material.LECTERN, 1,
                            ChatUtils.title("Previous Feedback"),
                            ChatUtils.line("Click to review previous"),
                            ChatUtils.line("feedback this player received"),
                            ChatUtils.line("while building this plot.")),
                    u -> {
                        // Open the previous feedback menu.
                        review.openPreviousFeedbackGui();
                    });
        }

        //Cancel review.
        setItem(26, Utils.createItem(Material.BARRIER, 1,
                        ChatUtils.title("Cancel Review"),
                        ChatUtils.line("Stop reviewing this plot.")),
                u -> {

                    //Remove the reviewer from the plot.
                    try {
                        WorldGuardFunctions.removeMember(String.valueOf(review.getPlotID()), u.player.getUniqueId().toString(), world);
                    } catch (RegionNotFoundException | RegionManagerNotFoundException e) {

                        u.player.sendMessage(ChatUtils.error("Unable to remove you from the plot, please notify an admin."));
                        e.printStackTrace();

                    }

                    // Set the plot back to submitted.
                    PlotHelper.updateSubmittedStatus(review.getPlotID(), SubmittedStatus.SUBMITTED);

                    //Send feedback.
                    u.player.sendMessage(ChatUtils.success("Cancelled reviewing of plot ")
                            .append(Component.text(review.getPlotID(), NamedTextColor.DARK_AQUA)));

                    //Close review.
                    u.player.closeInventory();
                    review.closeReview();

                });
    }

    public void refresh() {

        this.clearGui();
        createGui();

    }
}
