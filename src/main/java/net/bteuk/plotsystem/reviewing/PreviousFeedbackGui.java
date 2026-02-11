package net.bteuk.plotsystem.reviewing;

import net.bteuk.minecraft.gui.Gui;
import net.bteuk.minecraft.gui.GuiManager;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.SQLAPI;
import net.bteuk.network.api.plotsystem.ReviewFeedback;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.utils.User;
import net.bteuk.plotsystem.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class PreviousFeedbackGui extends Gui {

    private final int plotID;
    private final User user;

    private final PlotAPI plotAPI;

    private final SQLAPI globalSQL;

    public PreviousFeedbackGui(GuiManager guiManager, int plotID, User user, PlotAPI plotAPI, SQLAPI globalSQL) {
        super(guiManager, 45, Component.text("Previous Feedback", NamedTextColor.AQUA, TextDecoration.BOLD));

        this.plotID = plotID;
        this.user = user;
        this.plotAPI = plotAPI;
        this.globalSQL = globalSQL;

        createGui();
    }

    private void createGui() {

        // Get the plot owner uuid.
        String uuid = plotAPI.getPlotOwner(plotID);

        // Get the number of times the plot was denied for the current plot owner.
        int deniedCount = plotAPI.getDeniedPlotCount(plotID, uuid);

        // Slot count.
        int slot = 10;

        // Iterate through the deniedCount inversely.
        // We cap the number at 21, since we'd never expect a player to have more plots denied than that;
        // It also saves us having to create multiple pages.
        for (int i = deniedCount; i > 0; i--) {

            // If the slot is greater than the number that fit in a page, stop.
            if (slot > 34) {

                break;

            }

            // Add player to gui.
            int finalI = i;
            setItem(slot, Utils.createItem(Material.WRITTEN_BOOK, 1,
                            ChatUtils.title("Feedback for submission " + i),
                            ChatUtils.line("Click to view feedback for this submission."),
                            ChatUtils.line("Reviewed by ")
                                    .append(Component.text(globalSQL.getString("SELECT name FROM player_data WHERE uuid='" + plotAPI.getPlotReviewer(plotID, uuid, i) + "';"),
                                            NamedTextColor.GRAY))),

                    clickEvent ->

                    {
                        // Close the inventory.
                        clickEvent.getWhoClicked().closeInventory();

                        // Create the book.
                        int reviewId = plotAPI.getReviewId(plotID, uuid, finalI);

                        // Open the book.
                        clickEvent.getWhoClicked().openBook(ReviewFeedback.createFeedbackBook(globalSQL, plotAPI, reviewId));
                    });

            // Increase the slot accordingly.
            if (slot % 9 == 7) {
                // Increase row, basically add 3.
                slot += 3;
            } else {
                // Increase value by 1.
                slot++;
            }
        }

        // Return to the review menu.
        setItem(44, Utils.createItem(Material.SPRUCE_DOOR, 1,
                        ChatUtils.title("Return"),
                        ChatUtils.line("Go back to the review menu.")),

                clickEvent -> {
                    // Go back to the review gui.
                    if (clickEvent.getWhoClicked() instanceof Player player) {
                        player.closeInventory();
                        user.getReview().getReviewActionGui().open(player);
                    }
                }
        );
    }
}
