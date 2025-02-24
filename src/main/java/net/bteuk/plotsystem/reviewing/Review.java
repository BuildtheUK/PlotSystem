package net.bteuk.plotsystem.reviewing;

import com.sk89q.worldedit.math.BlockVector2;
import lombok.Getter;
import net.bteuk.network.Network;
import net.bteuk.network.lib.dto.ChatMessage;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.dto.PlotMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.lib.utils.Reviewing;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.NetworkUser;
import net.bteuk.network.utils.Role;
import net.bteuk.network.utils.Roles;
import net.bteuk.network.utils.Time;
import net.bteuk.network.utils.enums.PlotStatus;
import net.bteuk.network.utils.enums.SubmittedStatus;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.User;
import net.bteuk.plotsystem.utils.plugins.WorldEditor;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static net.bteuk.network.lib.enums.ChatChannels.REVIEWER;
import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class Review {

    private final PlotSQL plotSQL = PlotSystem.getInstance().plotSQL;

    // User instance.
    private final User user;

    // Plot id.
    @Getter
    private final int plotID;

    @Getter
    private final String plotOwner;

    @Getter
    private final ReviewMode mode;

    private final ItemStack[] initialInventory;

    // Review Gui and Listener.
    @Getter
    private final ReviewGui reviewGui;
    private final ReviewHotbar hotbarListener;

    // Previous feedback Gui.
    private PreviousFeedbackGui previousFeedbackGui;

    @Getter
    private final ReviewBook reviewBook;

    /**
     * Constructor to create a new review.
     *
     * @param instance instance of the plugin
     * @param plotID the plot to review
     * @param user the reviewer
     * @param mode the review mode
     */
    public Review(PlotSystem instance, int plotID, User user, ReviewMode mode) {

        this.user = user;
        this.plotID = plotID;
        this.mode = mode;

        // Save the users hotbar to revert to after reviewing.
        // Then clear their inventory and set it up for reviewing.
        initialInventory = user.player.getInventory().getContents();
        user.player.getInventory().clear();

        // Get plot owner.
        plotOwner = plotSQL.getString("SELECT uuid FROM plot_members WHERE id=" + plotID + " AND is_owner=1;");

        // Create the review gui.
        reviewGui = new ReviewGui(this);

        // Setup the hotbar for the reviewer.
        hotbarListener = new ReviewHotbar(PlotSystem.getInstance(), user);

        // Create the review book.
        reviewBook = new ReviewBook(instance, user.player, hotbarListener);

    }

    public void closeReview() {

        // Unregister Listeners
        hotbarListener.unregister();
        reviewBook.unregister();

        // Remove any existing guis.
        if (reviewGui != null) {
            reviewGui.delete();
        }
        if (previousFeedbackGui != null) {
            previousFeedbackGui.delete();
        }

        // Convert inventory back to how it was pre-review.
        user.player.getInventory().setContents(initialInventory);

        // Set review to null.
        user.setReview(null);
    }

    /**
     * Opens the review gui.
     */
    public void openReviewGui() {
        NetworkUser networkUser = Network.getInstance().getUser(user.player);
        if (networkUser != null) {
            networkUser.player.closeInventory();
            reviewGui.open(networkUser);
        }
    }

    public void openPreviousFeedbackGui() {
        if (previousFeedbackGui == null) {
            previousFeedbackGui = new PreviousFeedbackGui(plotID, user);
        }

        NetworkUser networkUser = Network.getInstance().getUser(user.player);
        if (networkUser != null) {
            networkUser.player.closeInventory();
            previousFeedbackGui.open(Network.getInstance().getUser(user.player));
        }

    }

    /**
     * Save the review.
     *
     * @param accept true if the plot should be accepted, false if denied
     */
    public void save(boolean accept) {

        double verificationChance = Reviewing.getReassessmentChance(plotSQL.getReviewerReputation(user.uuid));
        boolean requiresVerification = (Math.random() * 10) < verificationChance;

        // Create a review entry in the database.
        int reviewId = plotSQL.createReview(plotID, plotOwner, user.uuid, accept, !requiresVerification);

        // Save feedback for each category.
        reviewBook.saveFeedback(reviewId);

        if (requiresVerification) {
            setAwaitingVerification();
        } else {
            completeReview(accept);
        }

        sendReviewerMessage(accept);

        // Close gui and clear review if exists.
        this.closeReview();
    }

    private void sendReviewerMessage(boolean accept) {
        if (accept) {
            user.player.sendMessage(ChatUtils.success("Plot ")
                    .append(Component.text(plotID, NamedTextColor.DARK_AQUA))
                    .append(ChatUtils.success(" accepted.")));
        } else {
            user.player.sendMessage(ChatUtils.success("Plot ")
                    .append(Component.text(plotID, NamedTextColor.DARK_AQUA))
                    .append(ChatUtils.success(" has been denied.")));
        }
    }

    private void setAwaitingVerification() {
        // Update the submitted status of the plot to 'awaiting verification'.
        plotSQL.update("UPDATE plot_submission WHERE status='" + SubmittedStatus.AWAITING_VERIFICATION.database_value + "';");

        notifyReviewers();

        ChatMessage chatMessage = new ChatMessage(REVIEWER.getChannelName(), "server",
                ChatUtils.success("A submitted plot has been reviewed is awaiting verification."));
        Network.getInstance().getChat().sendSocketMesage(chatMessage);
    }

    private void completeReview(boolean accept) {

        // Get world of plot.
        World world = Bukkit.getWorld(plotSQL.getString("SELECT location FROM plot_data WHERE id=" + plotID + ";"));
        if (world == null) {
            LOGGER.warning("World of the plot is null!");
            return;
        }

        // Remove submitted plot entry.
        plotSQL.update("DELETE FROM plot_submission WHERE plot_id=" + plotID + ";");

        if (accept) {
            // Accept the plot.
            completePlot(world);
        } else {
            // Deny the plot.
            denyPlot(world);
        }
    }

    private void completePlot(World plotWorld) {

        // Remove plot members.
        plotSQL.update("DELETE FROM plot_members WHERE id=" + plotID + ";");

        // Set plot to 'completed'.
        PlotHelper.updatePlotStatus(plotID, PlotStatus.COMPLETED);

        // Copy the plot to the save world.
        savePlot(plotWorld);

        // Remove plot from worldguard.
        try {
            WorldGuardFunctions.delete(String.valueOf(plotID), plotWorld);
        } catch (RegionManagerNotFoundException e) {
            user.player.sendMessage(ChatUtils.error("An error occurred while removing the plot, please contact an admin."));
            e.printStackTrace();
            return;
        }

        updateRole();

        notifyPlotOwnerAccepted();
        notifyReviewers();
    }

    private void savePlot(World plotWorld) {

        // Get the save world.
        String save_world = PlotSystem.getInstance().getConfig().getString("save_world");
        if (save_world == null) {
            LOGGER.warning("Save world is not set in config!");
            return;
        }

        World saveWorld = Bukkit.getWorld(save_world);
        if (saveWorld == null) {
            LOGGER.warning("Save world is null!");
            return;
        }

        // Get the negative coordinate transform.
        int xTransform = -plotSQL.getInt("SELECT xTransform FROM location_data WHERE name='" + plotWorld.getName() + "';");
        int zTransform = -plotSQL.getInt("SELECT zTransform FROM location_data WHERE name='" + plotWorld.getName() + "';");

        List<BlockVector2> copyVector;

        try {
            copyVector = WorldGuardFunctions.getPoints(String.valueOf(plotID), plotWorld);
        } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
            //u.player.sendMessage(ChatUtils.error("An error occurred in the plot accepting process, please contact an admin."));
            e.printStackTrace();
            return;
        }

        // Create paste vector by taking the copy vector coordinate and adding the coordinate transform.
        List<BlockVector2> pasteVector = new ArrayList<>();
        for (BlockVector2 bv : copyVector) {
            pasteVector.add(BlockVector2.at(bv.getX() + xTransform, bv.getZ() + zTransform));
        }

        Bukkit.getScheduler().runTaskAsynchronously(PlotSystem.getInstance(), () -> {
            WorldEditor.updateWorld(copyVector, pasteVector, plotWorld, saveWorld);
            LOGGER.info("Plot " + plotID + " successfully saved.");
        });
    }

    private void denyPlot(World plotWorld) {

        // Update last visit time, to prevent inactivity removal of plot.
        plotSQL.update("UPDATE plot_members SET last_enter=" + Time.currentTime() + " WHERE id=" + plotID + ";");

        // Set status of the plot back to 'claimed'.
        PlotHelper.updatePlotStatus(plotID, PlotStatus.CLAIMED);

        // Remove the reviewer from the plot.
        try {
            WorldGuardFunctions.removeMember(String.valueOf(plotID), user.player.getUniqueId().toString(), plotWorld);
        } catch (RegionNotFoundException | RegionManagerNotFoundException e) {
            user.player.sendMessage(ChatUtils.error("Unable to remove you from the plot, please notify an admin."));
            e.printStackTrace();
        }

        //Send feedback.
        user.player.sendMessage(ChatUtils.success("Plot ")
                .append(Component.text(plotID, NamedTextColor.DARK_AQUA))
                .append(ChatUtils.success(" has been denied.")));

        notifyPlotOwnerDenied();
        notifyReviewers();
    }

    private void updateRole() {
        // Get the plot difficulty and player role.
        int difficulty = plotSQL.getInt("SELECT difficulty FROM plot_data WHERE id=" + plotID + ";");
        String builderRole = Roles.builderRole(plotOwner).join();
        LOGGER.info(String.format("Plot owner %s has builder role %s", plotOwner, builderRole));

        //Calculate the role the player will be promoted to, if any.
        String newRole = getNewRole(difficulty, builderRole);

        //Send a message to the plot owner letting them know their plot has been accepted.
        String discordMessage = "Plot " + plotID + " has been accepted.";
        if (newRole != null) {
            Role role = Roles.getRoles().stream().filter(r -> r.getId().equals(newRole)).findFirst().orElse(null);
            if (role != null) {
                discordMessage += "\nYou have been promoted to **" + role.getName() + "**";
            } else {
                LOGGER.warning(String.format("Role %s could not be found, check the Network roles.yml", newRole));
            }
            // Add the new role and remove the old one.
            String name = Network.getInstance().getGlobalSQL().getString("SELECT name FROM player_data WHERE uuid='" + plotOwner + "';");
            Roles.alterRole(plotOwner, name, newRole, false, true).join();
            Roles.alterRole(plotOwner, name, builderRole, true, false).join();
        } else {
            LOGGER.info("Plot was accepted but no new role was given.");
        }
    }

    private void notifyPlotOwnerAccepted() {
        // Send message to plot owner.
        DirectMessage directMessage = new DirectMessage("global", plotOwner, "server",
                ChatUtils.success("Plot %s has been accepted.", String.valueOf(plotID)), true);
        Network.getInstance().getChat().sendSocketMesage(directMessage);

        // TODO: Format the discord message.
//        if (!pages.isEmpty()) {
//            discordMessage += "\nFeedback: " + String.join(" ", pages);
//        }
//        DiscordDirectMessage discordDirectMessage = new DiscordDirectMessage(plotOwner, discordMessage);
//        Network.getInstance().getChat().sendSocketMesage(discordDirectMessage);
    }

    private void notifyPlotOwnerDenied() {
        DirectMessage directMessage = new DirectMessage("global", plotOwner, "server",
                ChatUtils.error("Plot %s has been denied, feedback has been provided in the plot menu.", String.valueOf(plotID))
                        .append(ChatUtils.error("\nClick here to view the feedback!")
                                .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, String.format("/plot feedback %d", plotID)))), true);
        Network.getInstance().getChat().sendSocketMesage(directMessage);

        // TODO: Format the discord message.
//        String discordMessage = "Plot " + plotID + " has been denied.\nFeedback: " + String.join(" ", pages);
//        DiscordDirectMessage discordDirectMessage = new DiscordDirectMessage(plotOwner, discordMessage);
//        Network.getInstance().getChat().sendSocketMesage(discordDirectMessage);
    }

    private static void notifyReviewers() {
        // Send message to reviewers that a plot has been reviewed.
        PlotMessage plotMessage = new PlotMessage("A plot has been reviewed, there %s %d submitted %s.");
        Network.getInstance().getChat().sendSocketMesage(plotMessage);
    }

    private static String getNewRole(int difficulty, String role) {
        String newRole = null;
        switch (difficulty) {
            case 1 -> {
                if (role.equals("applicant")) {
                    newRole = "apprentice";
                }
            }
            case 2 -> {
                if (role.equals("applicant") || role.equals("apprentice")) {
                    newRole = "jrbuilder";
                }
            }
            case 3 -> {
                if (role.equals("applicant") || role.equals("apprentice") || role.equals("jrbuilder")) {
                    newRole = "builder";
                }
            }
        }
        return newRole;
    }
}
