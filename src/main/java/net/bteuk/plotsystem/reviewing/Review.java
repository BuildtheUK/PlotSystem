package net.bteuk.plotsystem.reviewing;

import lombok.Getter;
import net.bteuk.network.Network;
import net.bteuk.network.lib.dto.PlotMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.lib.utils.Reviewing;
import net.bteuk.network.utils.enums.SubmittedStatus;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

@Getter
public class Review extends ReviewAction {

    // Review Gui and Listener.
    private final ReviewGui reviewActionGui;

    /**
     * Constructor to create a new review.
     *
     * @param instance instance of the plugin
     * @param plotID the plot to review
     * @param user the reviewer
     */
    public Review(PlotSystem instance, int plotID, User user) {
        super(instance, plotID, user);

        // Create the review gui.
        reviewActionGui = new ReviewGui(this);

    }

    @Override
    public void cancel() {
        // Set the plot back to 'submitted'.
        PlotHelper.updateSubmittedStatus(plotID, SubmittedStatus.SUBMITTED);

        // Send feedback.
        if (user.player.isOnline()) {
            user.player.sendMessage(ChatUtils.success("Cancelled reviewing of plot ")
                    .append(Component.text(plotID, NamedTextColor.DARK_AQUA)));
        }

        super.cancel();
    }

    /**
     * Save the review.
     *
     * @param accept true if the plot should be accepted, false if denied
     */
    public void save(boolean accept) {

        double verificationChance = Reviewing.getReassessmentChance(plotSQL.getReviewerReputation(user.uuid));
        boolean requiresVerification = Math.random() < verificationChance;

        // Create a review entry in the database.
        int reviewId = plotSQL.createReview(plotID, plotOwner, user.uuid, accept, !requiresVerification);

        // Save feedback for each category.
        saveFeedback(reviewId);

        if (requiresVerification) {
            setAwaitingVerification(accept);
        } else {
            completeReview(accept);
        }

        // Close gui and clear review if exists.
        this.closeReviewAction();
    }

    @Override
    protected void notifyReviewers() {
        // Send message to reviewers that a plot has been reviewed.
        PlotMessage plotMessage = new PlotMessage("A plot has been reviewed, there %s %s submitted %s.", false);
        Network.getInstance().getChat().sendSocketMesage(plotMessage);
    }

    private void saveFeedback(int reviewId) {
        getReviewBook().saveFeedback(reviewId);
    }

    private void setAwaitingVerification(boolean accept) {
        // Update the submitted status of the plot to 'awaiting verification'.
        plotSQL.update("UPDATE plot_submission WHERE status='" + SubmittedStatus.AWAITING_VERIFICATION.database_value + "';");

        notifyReviewers();

        // Send message to reviewers that a plot has been verified.
        PlotMessage plotMessage = new PlotMessage("A plot has been reviewed and is awaiting verification, there %s %s %s awaiting verification.", true);
        Network.getInstance().getChat().sendSocketMesage(plotMessage);

        sendReviewerVerificationMessage(accept);
    }

    private void sendReviewerVerificationMessage(boolean accept) {
        if (accept) {
            user.player.sendMessage(ChatUtils.success("Plot ")
                    .append(Component.text(plotID, NamedTextColor.DARK_AQUA))
                    .append(ChatUtils.success(" accepted, it is now awaiting verification.")));
        } else {
            user.player.sendMessage(ChatUtils.success("Plot ")
                    .append(Component.text(plotID, NamedTextColor.DARK_AQUA))
                    .append(ChatUtils.success(" has been denied, it is now awaiting verification.")));
        }
    }
}
