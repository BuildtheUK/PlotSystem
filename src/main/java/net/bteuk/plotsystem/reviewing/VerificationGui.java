package net.bteuk.plotsystem.reviewing;

import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;

public class VerificationGui extends ReviewActionGui {
    public VerificationGui(Verification verification) {
        super(Component.text("Verification Menu", NamedTextColor.AQUA, TextDecoration.BOLD), verification);
    }

    @Override
    protected void createGuiInfoItem() {
        setItem(4, Utils.createItem(Material.BOOK, 1,
                ChatUtils.title("Plot Info"),
                ChatUtils.line("Plot ID: " + reviewAction.getPlotID()),
                ChatUtils.line("Plot Owner: " + globalSQL.getString("SELECT name FROM player_data WHERE uuid='" + reviewAction.getPlotOwner() + "';")),
                ChatUtils.line("Plot Reviewer: " + globalSQL.getString("SELECT name FROM player_data WHERE uuid='" +
                        plotSQL.getString("SELECT reviewer FROM plot_review WHERE plot_id=" + reviewAction.getPlotID() + " AND completed=0") + "';"))
                )
        );
    }
}
