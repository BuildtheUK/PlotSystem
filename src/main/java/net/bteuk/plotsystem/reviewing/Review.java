package net.bteuk.plotsystem.reviewing;

import lombok.Getter;
import net.bteuk.network.Network;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.utils.NetworkUser;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.User;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

public class Review {

    // User instance.
    private final User user;

    // Plot id.
    @Getter
    private final int plotID;

    @Getter
    private final ReviewMode mode;

    private final ItemStack[] initialInventory;

    // Review Gui and Listener.
    @Getter
    private final ReviewGui reviewGui;
    private final ReviewHotbar hotbarListener;

    // Accept Gui and accept data.
    public AcceptGui acceptGui;

    // Previous feedback Gui.
    public PreviousFeedbackGui previousFeedbackGui;

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

        // Create the review gui.
        reviewGui = new ReviewGui(user, plotID);

        // Setup the hotbar for the reviewer.
        hotbarListener = new ReviewHotbar(PlotSystem.getInstance(), user);

        // Create the review book.
        reviewBook = new ReviewBook(instance, user.player, hotbarListener);

    }

    public void closeReview() {

        //Unregister Listeners
        hotbarListener.unregister();
        reviewBook.unregister();

        //Remove any existing guis.
        if (reviewGui != null) {
            reviewGui.delete();
        }
        if (acceptGui != null) {
            acceptGui.delete();
        }
        if (previousFeedbackGui != null) {
            previousFeedbackGui.delete();
        }

        //Convert inventory back to how it was pre-review.
        user.player.getInventory().setContents(initialInventory);

        //Set review to null.
        user.setReview(null);

    }

    public void openReviewGui() {
        NetworkUser networkUser = Network.getInstance().getUser(user.player);
        if (networkUser != null) {
            networkUser.player.closeInventory();
            reviewGui.open(networkUser);
        }
    }
}
