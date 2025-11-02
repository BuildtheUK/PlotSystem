package net.bteuk.plotsystem.events;

import java.util.Arrays;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class EventManager {

    public static void event(String uuid, String[] event) {

        LOGGER.info("Event: " + Arrays.toString(event));

        // Start the execution process by looking at the event message structure.
        switch (event[0]) {
            case "submit" -> SubmitEvent.event(uuid, event);
            case "leave" -> LeaveEvent.event(uuid, event);
            case "join" -> JoinEvent.event(uuid, event);
            case "kick" -> KickEvent.event(uuid, event); // TODO: Also exists in Network
            case "outlines" -> OutlinesEvent.event(uuid, event);
            case "verify" -> VerifyEvent.event(uuid, event);
        }
    }
}
