package net.bteuk.plotsystem.reviewing;

import net.bteuk.network.Network;
import net.bteuk.network.gui.Gui;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.GlobalSQL;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

public abstract class ReviewActionGui extends Gui {

    protected ReviewAction reviewAction;

    protected final GlobalSQL globalSQL;
    protected final PlotSQL plotSQL;

    public ReviewActionGui(Component title, ReviewAction reviewAction) {
        super(27, title);

        this.reviewAction = reviewAction;

        this.globalSQL = Network.getInstance().getGlobalSQL();
        this.plotSQL = Network.getInstance().getPlotSQL();

        createGui();
    }

    protected abstract void createGuiInfoItem();

    private void createGui() {

        createGuiInfoItem();

        setItem(12, Utils.createItem(Material.GRASS_BLOCK, 1,
                        ChatUtils.title("Before View"),
                        ChatUtils.line("Teleport to the plot before it was claimed.")),
                u -> reviewAction.toBeforeView());

        setItem(14, Utils.createItem(Material.STONE_BRICKS, 1,
                        ChatUtils.title("Current View"),
                        ChatUtils.line("Teleport to the current view of the plot.")),
                u -> reviewAction.toCurrentView());

        setItem(10, Utils.createItem(Material.LIME_CONCRETE, 1,
                        ChatUtils.title("Accept Plot"),
                        ChatUtils.line("Accept the plot.")),
                u -> {
                    // TODO: Check if the plot can be accepted with the current feedback settings.

                    // Accept the plot.
                    reviewAction.save(true);

                    u.player.closeInventory();
                });

        setItem(16, Utils.createItem(Material.RED_CONCRETE, 1,
                        ChatUtils.title("Deny Plot"),
                        ChatUtils.line("Deny the plot and return it to the plot owner.")),
                u -> {

                    // TODO: Check if the plot has feedback for all categories that are not sufficient.

                    reviewAction.save(false);

                    u.player.closeInventory();

                });

        //View previous feedback, if it exists.
        if (plotSQL.hasRow("SELECT 1 FROM plot_review WHERE uuid='" + reviewAction.getPlotOwner() + "' AND plot_id=" + reviewAction.getPlotID() + " AND accepted=0;")) {

            setItem(18, Utils.createItem(Material.LECTERN, 1,
                            ChatUtils.title("Previous Feedback"),
                            ChatUtils.line("Click to review previous"),
                            ChatUtils.line("feedback this player received"),
                            ChatUtils.line("while building this plot.")),
                    u -> {
                        // Open the previous feedback menu.
                        reviewAction.openPreviousFeedbackGui();
                    });
        }

        //Cancel review.
        setItem(26, Utils.createItem(Material.BARRIER, 1,
                        ChatUtils.title("Cancel Verification"),
                        ChatUtils.line("Stop verifying this plot.")),
                u -> reviewAction.cancel());
    }

    public void refresh() {
        this.clearGui();
        createGui();
    }
}
