package net.bteuk.plotsystem.reviewing;

import lombok.Getter;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.utils.enums.SubmittedStatus;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Verification extends ReviewAction {

    @Getter
    private final VerificationGui reviewActionGui;

    /**
     * Constructor to create a new review.
     *
     * @param instance instance of the plugin
     * @param plotID the plot to review
     * @param user the reviewer
     */
    public Verification(PlotSystem instance, int plotID, User user) {
        super(instance, plotID, user);

        // Create the review gui.
        reviewActionGui = new VerificationGui(this);

    }

    @Override
    public void save(boolean accept) {

        // TODO: Get the review id.

        // TODO: Save the updated feedback.

        // TODO: Update the review from the database.

        // TODO: Save the verification.

        // TODO: Accept/Deny the plot.

        // TODO: Close the verification.

    }

    @Override
    public void cancel() {
        // Set the plot back to 'awaiting verification'.
        PlotHelper.updateSubmittedStatus(plotID, SubmittedStatus.AWAITING_VERIFICATION);

        // Send feedback.
        user.player.sendMessage(ChatUtils.success("Cancelled verification of plot ")
                .append(Component.text(plotID, NamedTextColor.DARK_AQUA)));

        super.cancel();
    }
}
