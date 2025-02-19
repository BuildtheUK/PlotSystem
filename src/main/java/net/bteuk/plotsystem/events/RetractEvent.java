package net.bteuk.plotsystem.events;

import net.bteuk.network.Network;
import net.bteuk.network.lib.dto.ChatMessage;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.utils.enums.PlotStatus;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.kyori.adventure.text.Component;

public class RetractEvent {

    public static void event(String uuid, String[] event) {

        //Events for retracting
        if (event[1].equals("plot")) {

            //Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            Component message;

            //Check if plot is submitted.
            if (PlotSystem.getInstance().plotSQL.hasRow("SELECT id FROM plot_data WHERE id=" + id + " AND status='submitted';")) {

                //Set plot status to claimed.
                PlotHelper.updatePlotStatus(id, PlotStatus.CLAIMED);

                // Remove submitted plot entry.
                PlotSystem.getInstance().plotSQL.update("DELETE FROM plot_submission WHERE plot_id=" + id + ";");

                message = ChatUtils.success("Retracted submission for Plot %s", String.valueOf(id));

                //Get number of submitted plots.
                int plot_count = PlotSystem.getInstance().plotSQL.getInt("SELECT count(id) FROM plot_data WHERE status='submitted';");

                //Send message to reviewers that a plot submission has been retracted.
                ChatMessage chatMessage = new ChatMessage("reviewer", "server",
                        ChatUtils.success("A submitted plot has been retracted, there " + (plot_count == 1 ? "is" : "are") + " %s submitted " + (plot_count == 1 ? "plot" : "plots") + ".", String.valueOf(plot_count))
                );
                Network.getInstance().getChat().sendSocketMesage(chatMessage);

            } else {

                //If plot is not submitted set the message accordingly.
                message = ChatUtils.error("Plot submission can not be retracted as it is not currently submitted.");

            }

            DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                    message, true);
            Network.getInstance().getChat().sendSocketMesage(directMessage);

        }
    }
}
