package net.bteuk.plotsystem.reviewing;

import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.SQLAPI;
import net.bteuk.plotsystem.utils.Utils;
import net.kyori.adventure.text.Component;
import org.btuk.minecraft.gui.Gui;
import org.btuk.minecraft.gui.GuiManager;
import org.btuk.network.lib.utils.ChatUtils;
import org.bukkit.Material;

public abstract class ReviewActionGui extends Gui {

    protected ReviewAction reviewAction;

    protected final PlotAPI plotAPI;
    protected final SQLAPI globalSQL;

    public ReviewActionGui(GuiManager guiManager, Component title, ReviewAction reviewAction, PlotAPI plotAPI, SQLAPI globalSQL) {
        super(guiManager, 27, title);

        this.reviewAction = reviewAction;
        this.plotAPI = plotAPI;
        this.globalSQL = globalSQL;

        createGui();
    }

    protected abstract void createGuiInfoItem();

    protected abstract void createCancelReviewActionItem();

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
                u -> reviewAction.saveIfPossible(true));

        setItem(16, Utils.createItem(Material.RED_CONCRETE, 1,
                        ChatUtils.title("Deny Plot"),
                        ChatUtils.line("Deny the plot and return it to the plot owner.")),
                u -> reviewAction.saveIfPossible(false));

        // View previous feedback if it exists.
        if (plotAPI.getDeniedPlotCount(reviewAction.getPlotID(), reviewAction.getPlotOwner()) > 0) {

            setItem(18, Utils.createItem(Material.LECTERN, 1,
                            ChatUtils.title("Previous Feedback"),
                            ChatUtils.line("Click to review previous"),
                            ChatUtils.line("feedback this player received"),
                            ChatUtils.line("while building this plot.")),
                    u -> reviewAction.openPreviousFeedbackGui());
        }

        // Cancel review action.
        createCancelReviewActionItem();
    }
}
