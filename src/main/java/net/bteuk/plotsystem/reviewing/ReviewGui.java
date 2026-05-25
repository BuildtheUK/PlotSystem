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

public class ReviewGui extends ReviewActionGui {

    public ReviewGui(Review review, GuiManager guiManager, PlotAPI plotAPI, SQLAPI globalSQL) {
        super(guiManager, Component.text("Review Menu", NamedTextColor.AQUA, TextDecoration.BOLD), review, plotAPI, globalSQL);
    }

    @Override
    protected void createGuiInfoItem() {
        setItem(4, Utils.createItem(Material.BOOK, 1,
                ChatUtils.title("Plot Info"),
                ChatUtils.line("Plot ID: " + reviewAction.getPlotID()),
                ChatUtils.line("Plot Owner: " + globalSQL.getString("SELECT name FROM player_data WHERE uuid='" + reviewAction.getPlotOwner() + "';"))));
    }

    @Override
    protected void createCancelReviewActionItem() {
        //Cancel review.
        setItem(26, Utils.createItem(Material.BARRIER, 1,
                        ChatUtils.title("Cancel Review"),
                        ChatUtils.line("Stop reviewing this plot.")),
                u -> reviewAction.cancel());
    }
}
