package net.bteuk.plotsystem.events;

import net.bteuk.network.Network;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.dto.PlotMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.utils.Time;
import net.bteuk.network.utils.enums.PlotStatus;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.kyori.adventure.text.Component;

public class SubmitEvent {

    public static void event(String uuid, String[] event) {

        // Events for submitting
        if (event[1].equals("plot")) {

            // Convert the string id to int id.
            int plotID = Integer.parseInt(event[2]);

            // Check if the player can submit a plot at this point in time.
            long lCoolDown = PlotSystem.getInstance().getConfig().getInt("submit_cooldown") * 60L * 1000L;
            long lSubmit = PlotSystem.getInstance().globalSQL.getLong("SELECT last_submit FROM player_data WHERE uuid='" + uuid + "';");

            Component message;

            if (Time.currentTime() - lSubmit <= lCoolDown) {

                long lon_dif = lCoolDown - (Time.currentTime() - lSubmit);

                int sec = (int) ((lon_dif / 1000) % 60);
                int min = (int) ((lon_dif / 1000) / 60);

                String time;

                if (min == 0) {
                    time = sec + " second";
                } else {
                    if (sec == 0) {
                        time = min + " minute";
                    } else {
                        time = min + " minute and " + sec + " second";
                    }
                }

                message = ChatUtils.error("You have a %s cooldown before you can submit another plot.", time);

            } else {

                // Check if plot is claimed.
                if (PlotSystem.getInstance().plotSQL.hasRow("SELECT id FROM plot_data WHERE id=" + plotID + " AND status='claimed';")) {

                    // Create new submitted plot key.
                    PlotSystem.getInstance().plotSQL.update(
                            "INSERT INTO plot_submission(plot_id,submit_time,status,last_query) VALUES(" + plotID + "," + Time.currentTime() + ",'submitted'," + Time.currentTime() + ");");

                    // Set plot status to submitted.
                    PlotHelper.updatePlotStatus(plotID, PlotStatus.SUBMITTED);

                    // Update last submit time in playerdata.
                    PlotSystem.getInstance().globalSQL.update("UPDATE player_data SET last_submit=" + Time.currentTime() + " WHERE uuid='" + uuid + "';");

                    message = ChatUtils.success("Submitted plot %s", String.valueOf(plotID));

                    // Send message to reviewers that a plot has been submitted.
                    PlotMessage plotMessage = new PlotMessage("A plot has been submitted, there %s %s submitted %s.", false);
                    Network.getInstance().getChat().sendSocketMesage(plotMessage);

                } else {

                    // If plot is not claimed set the message accordingly.
                    message = ChatUtils.error("Plot can not be submitted");

                }
            }

            DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                    message, true);
            Network.getInstance().getChat().sendSocketMesage(directMessage);

        }
    }
}
