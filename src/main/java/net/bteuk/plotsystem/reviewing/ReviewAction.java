package net.bteuk.plotsystem.reviewing;

import com.sk89q.worldedit.math.BlockVector2;
import lombok.Getter;
import net.bteuk.network.Network;
import net.bteuk.network.gui.Gui;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.enums.PlotDifficulties;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.NetworkUser;
import net.bteuk.network.utils.Role;
import net.bteuk.network.utils.Roles;
import net.bteuk.network.utils.Time;
import net.bteuk.network.utils.enums.PlotStatus;
import net.bteuk.network.utils.plotsystem.ReviewCategory;
import net.bteuk.network.utils.plotsystem.ReviewSelection;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.exceptions.WorldNotFoundException;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.User;
import net.bteuk.plotsystem.utils.plugins.WorldEditor;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;
import static net.bteuk.plotsystem.utils.PlotValues.difficultyMaterial;

public abstract class ReviewAction {

    protected final PlotSQL plotSQL;

    // User instance.
    protected final User user;

    // Plot id.
    @Getter
    protected final int plotID;

    @Getter
    protected final String plotOwner;

    protected final World plotWorld;

    // Previous feedback Gui.
    protected PreviousFeedbackGui previousFeedbackGui;

    private final ItemStack[] initialInventory;

    private final ReviewHotbar hotbarListener;

    @Getter
    private final ReviewBook reviewBook;

    private PlotDifficulties plotDifficulty = PlotDifficulties.EASY;

    public ReviewAction(PlotSystem instance, int plotID, User user) {

        this.user = user;
        this.plotID = plotID;
        this.plotSQL = instance.plotSQL;

        // Get the plot world.
        this.plotWorld = Bukkit.getWorld(plotSQL.getString("SELECT location FROM plot_data WHERE id=" + plotID + ";"));

        // Get plot owner.
        this.plotOwner = plotSQL.getString("SELECT uuid FROM plot_members WHERE id=" + plotID + " AND is_owner=1;");

        // Save the users hotbar to revert to after reviewing.
        // Then clear their inventory and set it up for reviewing.
        initialInventory = user.player.getInventory().getContents();
        user.player.getInventory().clear();

        // Setup the hotbar for the reviewer.
        hotbarListener = new ReviewHotbar(PlotSystem.getInstance(), user);

        // Create the review book.
        reviewBook = new ReviewBook(instance, user.player, hotbarListener);

        int plotDifficulty = plotSQL.getInt("SELECT difficulty FROM plot_data WHERE id=" + plotID + ";");
        for (PlotDifficulties difficulty : PlotDifficulties.values()) {
            if (difficulty.getValue() == plotDifficulty) {
                this.plotDifficulty = difficulty;
                break;
            }
        }
    }

    public abstract Gui getReviewActionGui();

    protected abstract void notifyReviewers();

    protected abstract void save(boolean accept);

    /**
     * Closes the review action.
     */
    public void closeReviewAction() {
        // Unregister Listeners
        hotbarListener.unregister();
        reviewBook.unregister();

        // Remove any existing guis.
        if (getReviewActionGui() != null) {
            getReviewActionGui().delete();
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
     * Opens the review action gui.
     */
    public void openReviewActionGui() {
        NetworkUser networkUser = Network.getInstance().getUser(user.player);
        if (networkUser != null) {
            networkUser.player.closeInventory();
            getReviewActionGui().open(networkUser);
        }
    }

    /**
     * Cancel the review action.
     */
    public void cancel() {
        removeReviewerFromPlot();

        // Close review.
        if (user.player.isOnline()) {
            user.player.closeInventory();
        }
        closeReviewAction();
    }

    public void toBeforeView() {
        // Teleport to plot in original state.
        user.player.closeInventory();

        try {
            Location l = WorldGuardFunctions.getBeforeLocation(String.valueOf(plotID), plotWorld);
            user.player.teleport(l);
        } catch (RegionManagerNotFoundException | RegionNotFoundException | WorldNotFoundException e) {
            user.player.sendMessage(ChatUtils.error("Unable to teleport you to the before view of this plot, please contact an admin."));
            return;
        }

        // Try to create the outline of the before view.
        try {
            // Get outlines of the plot.
            List<BlockVector2> vector = WorldGuardFunctions.getPointsTransformedToSaveWorld(String.valueOf(plotID), plotWorld);

            // Get the plot difficulty.
            int difficulty = plotSQL.getInt("SELECT difficulty FROM plot_data WHERE id=" + plotID + ";");

            // Draw the outline.
            PlotSystem.getInstance().getOutlines().addOutline(user.player, vector, difficultyMaterial(difficulty).createBlockData());

        } catch (RegionNotFoundException | RegionManagerNotFoundException e) {
            user.player.sendMessage(ChatUtils.error("Outline could not be drawn in save world, please contact an admin!"));
        }
    }

    public void toCurrentView() {
        // Teleport to plot in current state.
        user.player.closeInventory();

        try {
            Location l = WorldGuardFunctions.getCurrentLocation(String.valueOf(plotID), plotWorld);
            user.player.teleport(l);
        } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
            user.player.sendMessage(ChatUtils.error("Unable to teleport you to the this plot, please contact an admin."));
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

    public void saveIfPossible(boolean accept) {
        if (canSave(accept)) {
            save(accept);
        }
        user.player.closeInventory();
    }

    /**
     * Indicated whether a plot can be accepted/denied with the current feedback settings.
     * @param accept whether the check should be done for accepting the plot
     * @return whether the plot can be accepted/denied
     */
    private boolean canSave(boolean accept) {

        boolean canSave = true;
        boolean thresholdsReached = true;
        for (ReviewCategory category : ReviewCategory.values()) {
            if (category.isRequired()) {

                ReviewSelection selection = reviewBook.getReviewSelectionForCategory(category);
                boolean thresholdReached = selection != null && PlotHelper.reviewCategoryReachedThreshold(plotDifficulty, category, selection);
                thresholdsReached = thresholdsReached && thresholdReached;

                if (accept && !thresholdReached) {
                    // Notify the reviewer that the plot can not be accepted with this category selection.
                    ReviewSelection requiredThreshold = PlotHelper.getReviewCategoryThreshold(plotDifficulty, category);
                    if (requiredThreshold == null) {
                        user.player.sendMessage(ChatUtils.error("Category %s does not have a configured threshold, please notify an administrator!", category.getDisplayName()));
                    } else {
                        user.player.sendMessage(ChatUtils.error("Category %s selection is not sufficient to accept this plot, must be at least ", category.getDisplayName()).append(requiredThreshold.getDisplayComponent()));
                    }
                    canSave = false;
                } else if (!accept && !thresholdReached && !reviewBook.hasFeedback(category)) {
                    user.player.sendMessage(ChatUtils.error("Category %s must have written feedback to deny this plot.", category.getDisplayName()));
                    canSave = false;
                }
            }
        }

        // If all the thresholds have been reached, but the plot is being denied, notify the reviewer.
        if (!accept && thresholdsReached) {
            user.player.sendMessage(ChatUtils.error("All required categories are sufficient to accept the plot."));
            canSave = false;
        }

        return canSave;
    }

    protected void completeReview(boolean accept) {

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

        sendReviewerMessage(accept);
    }

    protected void removeReviewerFromPlot() {
        // Remove the reviewer from the plot.
        try {
            WorldGuardFunctions.removeMember(String.valueOf(plotID), user.uuid, plotWorld);
        } catch (RegionNotFoundException | RegionManagerNotFoundException e) {
            user.player.sendMessage(ChatUtils.error("Unable to remove you from the plot, please notify an admin."));
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
        }

        updateRole();

        notifyPlotOwnerAccepted();
        notifyReviewers();
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
        }

        notifyPlotOwnerDenied();
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
