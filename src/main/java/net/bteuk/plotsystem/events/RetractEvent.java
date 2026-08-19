package net.bteuk.plotsystem.events;

import net.bteuk.network.api.ChatAPI;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.entity.Event;
import net.bteuk.network.api.plotsystem.PlotStatus;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.kyori.adventure.text.Component;
import org.btuk.network.lib.dto.DirectMessage;
import org.btuk.network.lib.dto.PlotMessage;
import org.btuk.network.lib.utils.ChatUtils;

public class RetractEvent implements Event {

    private final PlotAPI plotAPI;

    private final PlotHelper plotHelper;

    private final ChatAPI chatAPI;

    public RetractEvent(PlotAPI plotAPI, PlotHelper plotHelper, ChatAPI chatAPI) {
        this.plotAPI = plotAPI;
        this.plotHelper = plotHelper;
        this.chatAPI = chatAPI;
    }

    public void event(String uuid, String[] event, String sMessage) {

        // Events for retracting
        if (event[1].equals("plot")) {

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            Component message;

            // Check if the plot is submitted.
            if (plotAPI.getPlotStatus(id) == PlotStatus.SUBMITTED) {

                // Set plot status to 'claimed'.
                plotHelper.updatePlotStatus(id, PlotStatus.CLAIMED);

                // Remove the submitted plot entry.
                plotAPI.removePlotSubmission(id);

                // Update last submit time in player data so the player doesn't have a cooldown any more.
                plotAPI.updateLastSubmit(uuid, 0);

                message = ChatUtils.success("Retracted submission for Plot %s", String.valueOf(id));

                // Send a message to reviewers that a plot submission has been retracted.
                PlotMessage plotMessage = new PlotMessage("A submitted plot has been retracted, there %s %s submitted %s.", false);
                chatAPI.sendPlotMessage(plotMessage);

            } else {
                // If plot is not submitted set the message accordingly.
                message = ChatUtils.error("Plot submission can not be retracted as it is not currently submitted.");
            }

            DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                    message, true);
           chatAPI.sendDirectMessage(directMessage);

        }
    }
}
