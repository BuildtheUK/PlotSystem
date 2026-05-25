package net.bteuk.plotsystem.reviewing;

import org.btuk.minecraft.gui.GuiManager;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.SQLAPI;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class VerificationGui extends ReviewActionGui {

    private final Verification verification;

    public VerificationGui(Verification verification, GuiManager guiManager, PlotAPI plotAPI, SQLAPI globalSQL) {
        super(guiManager, Component.text("Verification Menu", NamedTextColor.AQUA, TextDecoration.BOLD), verification, plotAPI, globalSQL);
        this.verification = verification;
        createDisableReputationChange();
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

    private void createDisableReputationChange() {
        if (verification.isDisableReputationChange()) {
            ItemStack item = Utils.createItem(Material.LIGHTNING_ROD, 1, ChatUtils.title("Enable Reputation Change"),
                    ChatUtils.line("Currently disabled, click to enable reputation change for the reviewer of this plot."));
            setItem(8, item, clickEvent -> {
                verification.setDisableReputationChange(false);
                refresh((Player) clickEvent.getWhoClicked());
            });
        } else {
            ItemStack item = Utils.createItem(Material.LIGHTNING_ROD, 1, ChatUtils.title("Disable Reputation Change"),
                    ChatUtils.line("Currently enabled, click to disable reputation change for the reviewer of this plot."),
                    ChatUtils.line("Thi"));
            Utils.enchant(item);
            setItem(8, item, clickEvent -> {
                verification.setDisableReputationChange(true);
                refresh((Player) clickEvent.getWhoClicked());
            });
        }
    }

    private void refresh(Player player) {
        this.createDisableReputationChange();
        updatePlayerInventory(player);
    }
}
