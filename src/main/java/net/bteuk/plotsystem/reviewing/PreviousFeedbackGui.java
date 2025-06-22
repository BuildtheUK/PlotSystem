package net.bteuk.plotsystem.reviewing;

import net.bteuk.network.Network;
import net.bteuk.network.gui.Gui;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.GlobalSQL;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.Utils;
import net.bteuk.network.utils.plotsystem.ReviewFeedback;
import net.bteuk.plotsystem.utils.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;

public class PreviousFeedbackGui extends Gui {

    private final PlotSQL plotSQL;
    private final GlobalSQL globalSQL;

    private final int plotID;
    private final User user;

    public PreviousFeedbackGui(int plotID, User user) {

        super(45, Component.text("Previous Feedback", NamedTextColor.AQUA, TextDecoration.BOLD));

        this.plotID = plotID;
        this.user = user;

        //Get plot sql.
        plotSQL = Network.getInstance().getPlotSQL();

        //Get global sql.
        globalSQL = Network.getInstance().getGlobalSQL();

        createGui();

    }

    private void createGui() {

        //Get plot owner uuid.
        String uuid = plotSQL.getString("SELECT uuid FROM plot_members WHERE id=" + plotID + " AND is_owner=1;");

        //Get the number of times the plot was denied for the current plot owner.
        int deniedCount = plotSQL.getInt("SELECT COUNT(attempt) FROM plot_review WHERE plot_id=" + plotID + " AND uuid='" + uuid + "' AND completed=1 AND accepted=0;");

        //Slot count.
        int slot = 10;

        //Iterate through the deniedCount inversely.
        //We cap the number at 21, since we'd never expect a player to have more plots denied than that,
        //it also saves us having to create multiple pages.
        for (int i = deniedCount; i > 0; i--) {

            //If the slot is greater than the number that fit in a page, stop.
            if (slot > 34) {

                break;

            }

            //Add player to gui.
            int finalI = i;
            setItem(slot, Utils.createItem(Material.WRITTEN_BOOK, 1,
                            ChatUtils.title("Feedback for submission " + i),
                            ChatUtils.line("Click to view feedback for this submission."),
                            ChatUtils.line("Reviewed by ")
                                    .append(Component.text(globalSQL.getString("SELECT name FROM player_data WHERE uuid='"
                                            + plotSQL.getString("SELECT reviewer FROM plot_review WHERE plot_id=" + plotID + " AND uuid='" + uuid + "' AND attempt=" + i + ";") + "';"), NamedTextColor.GRAY))),

                    u ->

                    {
                        // Close the inventory.
                        u.player.closeInventory();

                        // Create book.
                        int reviewId = plotSQL.getInt("SELECT id FROM plot_review WHERE plot_id=" + plotID + " AND uuid='" + uuid + "' AND attempt=" + finalI + ";");

                        // Open the book.
                        u.player.openBook(ReviewFeedback.createFeedbackBook(reviewId));
                    });


            //Increase slot accordingly.
            if (slot % 9 == 7) {
                //Increase row, basically add 3.
                slot += 3;
            } else {
                //Increase value by 1.
                slot++;
            }
        }

        //Return to plot info menu.
        setItem(44, Utils.createItem(Material.SPRUCE_DOOR, 1,
                        ChatUtils.title("Return"),
                        ChatUtils.line("Go back to the review menu.")),

                u -> {
                    //Go back to the review gui.
                    u.player.closeInventory();
                    user.getReview().getReviewActionGui().open(u);
                }
        );
    }

    public void refresh() {
        this.clearGui();
        createGui();
    }


}
