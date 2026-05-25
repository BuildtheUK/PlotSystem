package net.bteuk.plotsystem.reviewing;

import com.sk89q.worldedit.math.BlockVector2;
import lombok.Getter;
import org.btuk.minecraft.gui.Gui;
import org.btuk.minecraft.gui.GuiManager;
import net.bteuk.network.api.ChatAPI;
import net.bteuk.network.api.NetworkAPI;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.RoleAPI;
import net.bteuk.network.api.SQLAPI;
import net.bteuk.network.api.entity.Role;
import net.bteuk.network.api.plotsystem.PlotStatus;
import net.bteuk.network.api.plotsystem.ReviewCategory;
import net.bteuk.network.api.plotsystem.ReviewSelection;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.dto.DiscordDirectMessage;
import net.bteuk.network.lib.enums.PlotDifficulties;
import net.bteuk.network.lib.utils.ChatUtils;
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

    protected final User user;

    @Getter
    protected final int plotID;

    protected final PlotAPI plotAPI;

    protected final PlotHelper plotHelper;

    private final SQLAPI globalSQL;

    private final GuiManager guiManager;

    protected final ChatAPI chatAPI;

    private final RoleAPI roleAPI;

    @Getter
    protected final String plotOwner;

    protected final World plotWorld;
    private final ItemStack[] initialInventory;
    private final ReviewHotbar hotbarListener;
    @Getter
    private final ReviewBook reviewBook;
    protected PreviousFeedbackGui previousFeedbackGui;
    protected PlotDifficulties plotDifficulty = PlotDifficulties.EASY;

    public ReviewAction(PlotSystem instance, int plotID, User user, NetworkAPI networkAPI, PlotHelper plotHelper, GuiManager guiManager) {

        this.user = user;
        this.plotID = plotID;
        this.plotAPI = networkAPI.getPlotAPI();
        this.plotHelper = plotHelper;
        this.globalSQL = networkAPI.getGlobalSQL();
        this.guiManager = guiManager;
        this.chatAPI = networkAPI.getChat();
        this.roleAPI = networkAPI.getRoleAPI();

        // Get the plot world.
        this.plotWorld = Bukkit.getWorld(plotAPI.getPlotLocation(plotID));

        // Get the plot owner.
        this.plotOwner = plotAPI.getPlotOwner(plotID);

        // Save the users hotbar to revert to after reviewing.
        // Then clear their inventory and set it up for reviewing.
        initialInventory = user.player.getInventory().getContents();
        user.player.getInventory().clear();

        // Set up the hotbar for the reviewer.
        hotbarListener = new ReviewHotbar(PlotSystem.getInstance(), user);

        // Create the review book.
        reviewBook = new ReviewBook(instance, user.player, hotbarListener, plotAPI);

        int plotDifficulty = plotAPI.getPlotDifficulty(plotID);
        for (PlotDifficulties difficulty : PlotDifficulties.values()) {
            if (difficulty.getValue() == plotDifficulty) {
                this.plotDifficulty = difficulty;
                break;
            }
        }
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
        user.player.closeInventory();
        getReviewActionGui().open(user.player);
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
        // Teleport to plot in its original state.
        user.player.closeInventory();

        try {
            Location l = WorldGuardFunctions.getBeforeLocation(String.valueOf(plotID), plotWorld, plotAPI);
            user.player.teleport(l);
        } catch (RegionManagerNotFoundException | RegionNotFoundException | WorldNotFoundException e) {
            user.player.sendMessage(ChatUtils.error("Unable to teleport you to the before view of this plot, please contact an admin."));
            return;
        }

        // Try to create the outline of the before view.
        try {
            // Get outlines of the plot.
            List<BlockVector2> vector = WorldGuardFunctions.getPointsTransformedToSaveWorld(String.valueOf(plotID), plotWorld, plotAPI);

            // Get the plot difficulty.
            int difficulty = plotAPI.getPlotDifficulty(plotID);

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
            previousFeedbackGui = new PreviousFeedbackGui(guiManager, plotID, user, plotAPI, globalSQL);
        }

        user.player.closeInventory();
        previousFeedbackGui.open(user.player);
    }

    public void saveIfPossible(boolean accept) {
        if (canSave(accept)) {
            save(accept);
        }
        user.player.closeInventory();
    }

    /**
     * Indicated whether a plot can be accepted/denied with the current feedback settings.
     *
     * @param accept whether the check should be done for accepting the plot
     * @return whether the plot can be accepted/denied
     */
    private boolean canSave(boolean accept) {

        boolean canSave = true;
        boolean thresholdsReached = true;
        for (ReviewCategory category : ReviewCategory.values()) {
            if (category.isRequired()) {

                ReviewSelection selection = reviewBook.getReviewSelectionForCategory(category);

                // If selection is NONE let the reviewer know, all required categories require a selection.
                if (selection == null || selection == ReviewSelection.NONE) {
                    user.player.sendMessage(ChatUtils.error("Category %s must have a selection.", category.getDisplayName()));
                    canSave = false;
                } else {
                    boolean thresholdReached = plotHelper.reviewCategoryThresholdReached(plotDifficulty, category, selection);
                    thresholdsReached = thresholdsReached && thresholdReached;

                    if (accept && !thresholdReached) {
                        // Notify the reviewer that the plot can not be accepted with this category selection.
                        ReviewSelection requiredThreshold = plotHelper.getReviewCategoryThreshold(plotDifficulty, category);
                        if (requiredThreshold == null) {
                            user.player.sendMessage(
                                    ChatUtils.error("Category %s does not have a configured threshold, please notify an administrator!", category.getDisplayName()));
                        } else {
                            user.player.sendMessage(ChatUtils.error("Category %s selection is not sufficient to accept this plot, must be at least ", category.getDisplayName())
                                    .append(requiredThreshold.getDisplayComponent()));
                        }
                        canSave = false;
                    } else if (!accept && !thresholdReached && !reviewBook.hasFeedback(category)) {
                        user.player.sendMessage(ChatUtils.error("Category %s must have written feedback to deny this plot.", category.getDisplayName()));
                        canSave = false;
                    }
                }
            }
        }

        // If all the thresholds have been reached, but the plot is being denied, notify the reviewer.
        if (!accept && thresholdsReached && canSave) {
            user.player.sendMessage(ChatUtils.error("All required categories are sufficient to accept the plot."));
            canSave = false;
        }

        return canSave;
    }

    protected void completeReview(boolean accept) {

        // Get world of plot.
        World world = Bukkit.getWorld(plotAPI.getPlotLocation(plotID));
        if (world == null) {
            LOGGER.warning("World of the plot is null!");
            return;
        }

        // Remove the submitted plot entry.
        plotAPI.removePlotSubmission(plotID);

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
        plotAPI.clearPlotMembers(plotID);

        // Set plot to 'completed'.
        plotHelper.updatePlotStatus(plotID, PlotStatus.COMPLETED);

        // Copy the plot to the save world.
        savePlot(plotWorld);

        // Remove plot from worldguard.
        try {
            WorldGuardFunctions.delete(String.valueOf(plotID), plotWorld);
        } catch (RegionManagerNotFoundException e) {
            user.player.sendMessage(ChatUtils.error("An error occurred while removing the plot, please contact an admin."));
        }

        notifyPlotOwnerAccepted();
        notifyReviewers();

        updateRole();
    }

    private void denyPlot(World plotWorld) {

        // Update last visit time for the owner to prevent inactivity removal of the plot.
        plotAPI.setPlotLastEnter(plotID, plotOwner);

        // Set status of the plot back to 'claimed'.
        plotHelper.updatePlotStatus(plotID, PlotStatus.CLAIMED);

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

        // Get the save-world.
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
        int xTransform = -plotAPI.getXTransform(plotWorld.getName());
        int zTransform = -plotAPI.getZTransform(plotWorld.getName());

        List<BlockVector2> copyVector;

        try {
            copyVector = WorldGuardFunctions.getPoints(String.valueOf(plotID), plotWorld);
        } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
            user.player.sendMessage(ChatUtils.error("An error occurred in the plot accepting process, please contact an admin."));
            LOGGER.severe("An error occurred in the plot accepting process, please contact an admin: " + e.getMessage());
            return;
        }

        // Create the paste vector by taking the copy vector coordinate and adding the coordinate transform.
        List<BlockVector2> pasteVector = new ArrayList<>();
        for (BlockVector2 bv : copyVector) {
            pasteVector.add(BlockVector2.at(bv.x() + xTransform, bv.z() + zTransform));
        }

        Bukkit.getScheduler().runTaskAsynchronously(PlotSystem.getInstance(), () -> {
            WorldEditor.updateWorld(copyVector, pasteVector, plotWorld, saveWorld);
            LOGGER.info("Plot " + plotID + " successfully saved.");
        });
    }

    private void updateRole() {
        // Get the plot difficulty and player role.
        int difficulty = plotAPI.getPlotDifficulty(plotID);
        String builderRole = roleAPI.getBuilderRole(plotOwner).join();
        LOGGER.info(String.format("Plot owner %s has builder role %s", plotOwner, builderRole));

        // Calculate the role the player will be promoted to, if any.
        String newRole = getNewRole(difficulty, builderRole);

        if (newRole != null) {
            Role role = roleAPI.getRoles().stream().filter(r -> r.getId().equals(newRole)).findFirst().orElse(null);
            if (role != null) {
                DiscordDirectMessage discordDirectMessage = new DiscordDirectMessage(plotOwner, "You have been promoted to **" + role.getName() + "**");
                chatAPI.sendDiscordDirectMessage(discordDirectMessage);
            } else {
                LOGGER.warning(String.format("Role %s could not be found, check the Network roles.yml", newRole));
            }
            // Add the new role and remove the old one.
            String name = globalSQL.getString("SELECT name FROM player_data WHERE uuid='" + plotOwner + "';");
            roleAPI.alterRole(plotOwner, name, newRole, false, true).join();
            roleAPI.alterRole(plotOwner, name, builderRole, true, false).join();
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
        // Send a message to the plot owner.
        DirectMessage directMessage = new DirectMessage("global", plotOwner, "server",
                ChatUtils.success("Plot %s has been accepted.", String.valueOf(plotID)), true);
        chatAPI.sendDirectMessage(directMessage);

        StringBuilder discordMessage = new StringBuilder("Plot " + plotID + " has been accepted");
        addFeedbackToDiscordMessage(discordMessage);
        sendDiscordMessage(discordMessage);
    }

    private void notifyPlotOwnerDenied() {
        DirectMessage directMessage = new DirectMessage("global", plotOwner, "server",
                ChatUtils.error("Plot %s has been denied, feedback has been provided in the plot menu.", String.valueOf(plotID))
                        .append(ChatUtils.error("\nClick here to view the feedback!")
                                .clickEvent(ClickEvent.runCommand(String.format("/plot feedback %d", plotID)))), true);
        chatAPI.sendDirectMessage(directMessage);

        StringBuilder discordMessage = new StringBuilder("Plot " + plotID + " has been denied.");
        addFeedbackToDiscordMessage(discordMessage);
        sendDiscordMessage(discordMessage);
    }

    private void addFeedbackToDiscordMessage(StringBuilder builder) {
        for (ReviewCategory category : ReviewCategory.values()) {
            if (reviewBook.hasFeedback(category)) {
                builder.append("\n\n__**").append(category.getDisplayName()).append(" feedback:**__");
                builder.append("\n").append(String.join(" ", reviewBook.getFeedbackForCategory(category)));
            }
        }
    }

    private void sendDiscordMessage(StringBuilder builder) {
        // Split the feedback per 2000 characters if necessary.
        for (int i = 0; i < builder.length(); i += 2000) {
            DiscordDirectMessage discordDirectMessage = new DiscordDirectMessage(plotOwner, builder.substring(i, Math.min(i + 2000, builder.length())));
            chatAPI.sendDiscordDirectMessage(discordDirectMessage);
        }
    }
}
