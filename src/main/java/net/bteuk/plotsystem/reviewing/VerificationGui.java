package net.bteuk.plotsystem.reviewing;

import net.bteuk.minecraft.gui.GuiManager;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.SQLAPI;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;

public class VerificationGui extends ReviewActionGui {
    public VerificationGui(Verification verification, GuiManager guiManager, PlotAPI plotAPI, SQLAPI globalSQL) {
        super(guiManager, Component.text("Verification Menu", NamedTextColor.AQUA, TextDecoration.BOLD), verification, plotAPI, globalSQL);
    }

    @Override
    protected void createGuiInfoItem() {
        int reviewId = plotAPI.getActiveReviewId(reviewAction.getPlotID());
        setItem(4, Utils.createItem(Material.BOOK, 1, ChatUtils.title("Plot Info"),
                ChatUtils.line("Plot ID: " + reviewAction.getPlotID()),
                ChatUtils.line("Plot Owner: " + globalSQL.getString("SELECT name FROM player_data WHERE uuid='" + reviewAction.getPlotOwner() + "';")),
                ChatUtils.line("Plot Reviewer: " + globalSQL.getString("SELECT name FROM player_data WHERE uuid='" + plotAPI.getPlotReviewer(reviewId) + "';")),
                ChatUtils.line("The reviewer ").append(ChatUtils.line(plotAPI.getReviewOutcome(reviewId) ? "accepted" : "denied")).append(ChatUtils.line(" this plot."))));
    }

    @Override
    protected void createCancelReviewActionItem() {
        // Cancel review.
        setItem(26, Utils.createItem(Material.BARRIER, 1,
                        ChatUtils.title("Cancel Verification"),
                        ChatUtils.line("Stop verifying this plot.")),
                u -> reviewAction.cancel());
    }
}
