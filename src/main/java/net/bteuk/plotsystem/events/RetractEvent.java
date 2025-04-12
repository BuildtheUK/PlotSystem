package net.bteuk.plotsystem.events;

import net.bteuk.network.Network;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.dto.PlotMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.utils.enums.PlotStatus;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.kyori.adventure.text.Component;

public class RetractEvent {

    public static void event(String uuid, String[] event) {

        // Events for retracting
        if (event[1].equals("plot")) {

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            Component message;

            // Check if plot is submitted.
            if (PlotSystem.getInstance().plotSQL.hasRow("SELECT id FROM plot_data WHERE id=" + id + " AND status='submitted';")) {

                // Set plot status to claimed.
                PlotHelper.updatePlotStatus(id, PlotStatus.CLAIMED);

                // Remove submitted plot entry.
                PlotSystem.getInstance().plotSQL.update("DELETE FROM plot_submission WHERE plot_id=" + id + ";");

                // Update last submit time in playerdata so the player doesn't have a cooldown anymore..
                PlotSystem.getInstance().globalSQL.update("UPDATE player_data SET last_submit=0 WHERE uuid='" + uuid + "';");

                message = ChatUtils.success("Retracted submission for Plot %s", String.valueOf(id));

                // Send message to reviewers that a plot submission has been retracted.
                PlotMessage plotMessage = new PlotMessage("A submitted plot has been retracted, there %s %s submitted %s.", false);
                Network.getInstance().getChat().sendSocketMesage(plotMessage);

            } else {

                // If plot is not submitted set the message accordingly.
                message = ChatUtils.error("Plot submission can not be retracted as it is not currently submitted.");

            }

            DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                    message, true);
            Network.getInstance().getChat().sendSocketMesage(directMessage);

        }
    }
}
