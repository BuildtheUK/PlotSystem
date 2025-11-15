package net.bteuk.plotsystem.events;

import net.bteuk.network.api.ChatAPI;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.SQLAPI;
import net.bteuk.network.api.entity.Event;
import net.bteuk.network.api.plotsystem.PlotStatus;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.dto.PlotMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.kyori.adventure.text.Component;

public class SubmitEvent implements Event {

    private final PlotAPI plotAPI;

    private final PlotHelper plotHelper;

    private final SQLAPI globalSQL;

    private final ChatAPI chatAPI;

    public SubmitEvent(PlotAPI plotAPI, PlotHelper plotHelper, SQLAPI globalSQL, ChatAPI chatAPI) {
        this.plotAPI = plotAPI;
        this.plotHelper = plotHelper;
        this.globalSQL = globalSQL;
        this.chatAPI = chatAPI;
    }

    public void event(String uuid, String[] event, String sMessage) {

        // Events for submitting
        if (event[1].equals("plot")) {

            // Convert the string id to int id.
            int plotID = Integer.parseInt(event[2]);

            // Check if the player can submit a plot at this point in time.
            long lCoolDown = PlotSystem.getInstance().getConfig().getInt("submit_cooldown") * 60L * 1000L;
            long lSubmit = globalSQL.getLong("SELECT last_submit FROM player_data WHERE uuid='" + uuid + "';");

            Component message;

            if (System.currentTimeMillis() - lSubmit <= lCoolDown) {

                long lon_dif = lCoolDown - (System.currentTimeMillis()  - lSubmit);

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
                if (plotAPI.isPlotClaimed(plotID)) {

                    // Create new submitted plot key.
                    plotAPI.createPlotSubmission(plotID);

                    // Set plot status to submitted.
                    plotHelper.updatePlotStatus(plotID, PlotStatus.SUBMITTED);

                    // Update last submit time.
                    plotAPI.updateLastSubmit(uuid, System.currentTimeMillis());

                    message = ChatUtils.success("Submitted plot %s", String.valueOf(plotID));

                    // Send message to reviewers that a plot has been submitted.
                    PlotMessage plotMessage = new PlotMessage("A plot has been submitted, there %s %s submitted %s.", false);
                    chatAPI.sendPlotMessage(plotMessage);

                } else {

                    // If plot is not claimed set the message accordingly.
                    message = ChatUtils.error("Plot can not be submitted");

                }
            }

            DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                    message, true);
            chatAPI.sendDirectMessage(directMessage);

        }
    }
}
