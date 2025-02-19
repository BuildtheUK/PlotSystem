package net.bteuk.plotsystem.reviewing;

import com.sk89q.worldedit.math.BlockVector2;
import net.bteuk.network.Network;
import net.bteuk.network.gui.Gui;
import net.bteuk.network.lib.dto.ChatMessage;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.dto.DiscordDirectMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.GlobalSQL;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.Time;
import net.bteuk.network.utils.Utils;
import net.bteuk.network.utils.enums.PlotStatus;
import net.bteuk.network.utils.enums.SubmittedStatus;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.exceptions.WorldNotFoundException;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.User;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

import static net.bteuk.plotsystem.utils.PlotValues.difficultyMaterial;

public class ReviewGui extends Gui {

    private final GlobalSQL globalSQL;
    private final PlotSQL plotSQL;

    private final User user;

    private final String plotOwner;
    private final World world;

    private final int plotID;

    public ReviewGui(User user, int plotID) {

        super(27, Component.text("Review Menu", NamedTextColor.AQUA, TextDecoration.BOLD));

        this.user = user;

        globalSQL = Network.getInstance().getGlobalSQL();
        plotSQL = Network.getInstance().getPlotSQL();

        this.plotID = plotID;

        //Get plot owner.
        plotOwner = plotSQL.getString("SELECT uuid FROM plot_members WHERE id=" + plotID + " AND is_owner=1;");

        //Get world of plot.
        world = Bukkit.getWorld(plotSQL.getString("SELECT location FROM plot_data WHERE id=" + plotID + ";"));

        createGui();

    }

    private void createGui() {

        setItem(4, Utils.createItem(Material.BOOK, 1,
                ChatUtils.title("Plot Info"),
                ChatUtils.line("Plot ID: " + plotID),
                ChatUtils.line("Plot Owner: " + globalSQL.getString("SELECT name FROM player_data WHERE uuid='" + plotOwner + "';"))));

        setItem(12, Utils.createItem(Material.GRASS_BLOCK, 1,
                        ChatUtils.title("Before View"),
                        ChatUtils.line("Teleport to the plot before it was claimed.")),
                u -> {

                    //Teleport to plot in original state.
                    u.player.closeInventory();

                    try {
                        Location l = WorldGuardFunctions.getBeforeLocation(String.valueOf(plotID), world);
                        u.player.teleport(l);
                    } catch (RegionManagerNotFoundException | RegionNotFoundException | WorldNotFoundException e) {
                        u.player.sendMessage(ChatUtils.error("Unable to teleport you to the before view of this plot, please contact an admin."));
                        e.printStackTrace();
                    }

                    //Try to create the outline of the before view.
                    try {

                        //Get outlines of the plot.
                        List<BlockVector2> vector = WorldGuardFunctions.getPointsTransformedToSaveWorld(String.valueOf(plotID), world);

                        //Get the plot difficulty.
                        int difficulty = plotSQL.getInt("SELECT difficulty FROM plot_data WHERE id=" + plotID + ";");

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
                        Location l = WorldGuardFunctions.getCurrentLocation(String.valueOf(plotID), world);
                        u.player.teleport(l);
                    } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
                        u.player.sendMessage(ChatUtils.error("Unable to teleport you to the this plot, please contact an admin."));
                        e.printStackTrace();
                    }

                });

        setItem(10, Utils.createItem(Material.LIME_CONCRETE, 1,
                        ChatUtils.title("Accept Plot"),
                        ChatUtils.line("Opens the accept gui.")),
                u -> {

                    //Open accept gui, create a new one if it is null.
                    if (user.getReview().acceptGui == null) {
                        user.getReview().acceptGui = new AcceptGui(user, plotID);
                    }

                    //Open accept gui.
                    u.player.closeInventory();
                    user.getReview().acceptGui.open(u);

                });

        setItem(16, Utils.createItem(Material.RED_CONCRETE, 1,
                        ChatUtils.title("Deny Plot"),
                        ChatUtils.line("Deny the plot and return it to the plot owner.")),
                u -> {

                    //Close inventory.
                    u.player.closeInventory();

                    //Check if the feedback book has been edited.
                    if (!user.getReview().getFeedbackBook().isEdited()) {

                        u.player.sendMessage(ChatUtils.error("You must provide feedback to deny the plot."));
                        return;

                    }

                    //Get the feedback written in the book.
                    List<Component> book = user.getReview().getFeedbackBook().getBookPages();
                    List<String> pages = new ArrayList<>();
                    //Create new book id.
                    int bookID = 1 + plotSQL.getInt("SELECT id FROM book_data ORDER BY id DESC;");

                    //Iterate through all pages and store them in database.
                    int i = 1;

                    for (Component text : book) {
                        String page = PlainTextComponentSerializer.plainText().serialize(text);
                        pages.add(page);
                        //Add escape characters to '
                        if (!(plotSQL.update("INSERT INTO book_data(id,page,contents) VALUES(" + bookID + "," + i + ",'" + page.replace("'", "\\'") + "');"))) {
                            u.player.sendMessage(ChatUtils.error("An error occurred, please notify an admin."));
                            return;
                        }
                        i++;
                    }

                    //Update deny data.
                    if (plotSQL.update("INSERT INTO deny_data(id,uuid,reviewer,book_id,attempt,deny_time) VALUES(" + plotID + ",'" +
                            plotOwner + "','" + u.player.getUniqueId() + "'," + bookID + "," +
                            (1 + plotSQL.getInt("SELECT COUNT(attempt) FROM deny_data WHERE id=" + plotID + " AND uuid='" + plotOwner + "';")) +
                            "," + Time.currentTime() + ");")) {

                        //Send message to plot owner.
                        DirectMessage directMessage = new DirectMessage("global", plotOwner, "server",
                                ChatUtils.error("Plot %s has been denied, feedback has been provided in the plot menu.", String.valueOf(plotID))
                                        .append(ChatUtils.error("\nClick here to view the feedback!")
                                                .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, String.format("/plot feedback %d", plotID)))), true);
                        Network.getInstance().getChat().sendSocketMesage(directMessage);

                        // Remove submitted plot entry.
                        // TODO: Possible verification.
                        plotSQL.update("DELETE FROM plot_submission WHERE plot_id=" + plotID + ";");

                        //Update last visit time, to prevent inactivity removal of plot.
                        plotSQL.update("UPDATE plot_members SET last_enter=" + Time.currentTime() + " WHERE id=" + plotID + ";");

                        //Set status of plot back to claimed.
                        PlotHelper.updatePlotStatus(plotID, PlotStatus.CLAIMED);

                        //Remove the reviewer from the plot.
                        try {
                            WorldGuardFunctions.removeMember(String.valueOf(plotID), u.player.getUniqueId().toString(), world);
                        } catch (RegionNotFoundException | RegionManagerNotFoundException e) {
                            u.player.sendMessage(ChatUtils.error("Unable to remove you from the plot, please notify an admin."));
                            e.printStackTrace();
                        }

                        //Send feedback.
                        u.player.sendMessage(ChatUtils.success("Plot ")
                                .append(Component.text(plotID, NamedTextColor.DARK_AQUA))
                                .append(ChatUtils.success(" has been denied.")));

                        //Get number of submitted plots.
                        int plot_count = plotSQL.getInt("SELECT count(id) FROM plot_data WHERE status='submitted';");

                        // Send message to reviewers that a plot has been reviewed.
                        ChatMessage chatMessage = new ChatMessage("reviewer", "server",
                                ChatUtils.success("A plot has been reviewed, there " + (plot_count == 1 ? "is" : "are") + " %s submitted " + (plot_count == 1 ? "plot" : "plots") + ".", String.valueOf(plot_count))
                        );
                        Network.getInstance().getChat().sendSocketMesage(chatMessage);

                        // Send a message to the plot owner letting them know their plot has been denied.
                        String discordMessage = "Plot " + plotID + " has been denied.\nFeedback: " + String.join(" ", pages);
                        DiscordDirectMessage discordDirectMessage = new DiscordDirectMessage(plotOwner, discordMessage);
                        Network.getInstance().getChat().sendSocketMesage(discordDirectMessage);

                        // Close review.
                        u.player.closeInventory();
                        user.getReview().closeReview();

                    } else {

                        u.player.sendMessage(ChatUtils.error("An error occurred, please notify an admin."));

                    }
                });

        //View previous feedback, if it exists.
        if (plotSQL.hasRow("SELECT id FROM deny_data WHERE uuid='" + plotOwner + "' AND id=" + plotID + ";")) {

            setItem(18, Utils.createItem(Material.LECTERN, 1,
                            ChatUtils.title("Previous Feedback"),
                            ChatUtils.line("Click to review previous"),
                            ChatUtils.line("feedback this player received"),
                            ChatUtils.line("while building this plot.")),
                    u -> {

                        //Open the previous feedback menu.
                        if (user.getReview().previousFeedbackGui == null) {
                            user.getReview().previousFeedbackGui = new PreviousFeedbackGui(plotID, user);
                        }

                        u.player.closeInventory();
                        user.getReview().previousFeedbackGui.open(u);

                    });
        }

        //Cancel review.
        setItem(26, Utils.createItem(Material.BARRIER, 1,
                        ChatUtils.title("Cancel Review"),
                        ChatUtils.line("Stop reviewing this plot.")),
                u -> {

                    //Remove the reviewer from the plot.
                    try {
                        WorldGuardFunctions.removeMember(String.valueOf(plotID), u.player.getUniqueId().toString(), world);
                    } catch (RegionNotFoundException | RegionManagerNotFoundException e) {

                        u.player.sendMessage(ChatUtils.error("Unable to remove you from the plot, please notify an admin."));
                        e.printStackTrace();

                    }

                    // Set the plot back to submitted.
                    PlotHelper.updateSubmittedStatus(plotID, SubmittedStatus.SUBMITTED);

                    //Send feedback.
                    u.player.sendMessage(ChatUtils.success("Cancelled reviewing of plot ")
                            .append(Component.text(plotID, NamedTextColor.DARK_AQUA)));

                    //Close review.
                    u.player.closeInventory();
                    user.getReview().closeReview();

                });
    }

    public void refresh() {

        this.clearGui();
        createGui();

    }
}
