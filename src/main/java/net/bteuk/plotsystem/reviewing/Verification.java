package net.bteuk.plotsystem.reviewing;

import lombok.Getter;
import net.bteuk.network.Network;
import net.bteuk.network.lib.dto.PlotMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.utils.enums.SubmittedStatus;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class Verification extends ReviewAction {

    private final VerificationGui reviewActionGui;

    private final int reviewId;

    private final Map<ReviewCategory, ReviewCategoryFeedback> previousReviewFeedback = new HashMap<>();

    /**
     * Constructor to create a new review.
     *
     * @param instance instance of the plugin
     * @param plotID the plot to review
     * @param user the reviewer
     */
    public Verification(PlotSystem instance, int plotID, User user) {
        super(instance, plotID, user);

        this.reviewId = plotSQL.getInt("SELECT id FROM plot_review WHERE plot_id=" + plotID + " AND completed=0;");

        // Get the feedback from the reviewer.
        List<String> reviewCategories = plotSQL.getStringList("SELECT category FROM plot_category_feedback WHERE review_id=" + reviewId + ";");
        for (String category : reviewCategories) {
            previousReviewFeedback.put(ReviewCategory.valueOf(category), new ReviewCategoryFeedback(
                    ReviewCategory.valueOf(category),
                    ReviewSelection.valueOf(plotSQL.getString("SELECT selection FROM plot_category_feedback WHERE review_id=" + reviewId + " AND category='" + category + "';")),
                    plotSQL.getInt("SELECT book_id FROM plot_category_feedback WHERE review_id=" + reviewId + " AND category='" + category + "';")
            ));
        }

        getReviewBook().initReviewBook(previousReviewFeedback.values());

        // Create the review gui.
        reviewActionGui = new VerificationGui(this);

    }

    @Override
    public void save(boolean accept) {

        updateFeedback(reviewId, previousReviewFeedback);
        plotSQL.update("UPDATE plot_review SET accepted=" + accept + ", completed=1");

        completeReview(accept);

        // Close gui and clear review if exists.
        this.closeReviewAction();
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

    /**
     * Update the review feedback and return a hashmap of the altered feedback.
     *
     * @param previousReviewFeedback the previous review feedback
     */
    private void updateFeedback(int reviewId, Map<ReviewCategory, ReviewCategoryFeedback> previousReviewFeedback) {
        Map<ReviewCategory, ReviewCategoryFeedback> updatedReviewFeedback = getReviewBook().updateFeedback(reviewId, previousReviewFeedback);

        // Store the plot_verification_feedback for each category.
        for (ReviewCategoryFeedback categoryFeedback : previousReviewFeedback.values()) {

            ReviewCategoryFeedback updatedCategoryFeedback = updatedReviewFeedback.get(categoryFeedback.category());

            // Only store the category if there is updated feedback.
            if (updatedCategoryFeedback != null) {
                plotSQL.savePlotVerificationFeedback(reviewId, categoryFeedback.category().name(), user.uuid,
                        categoryFeedback.selection().name(), updatedCategoryFeedback.selection().name(),
                        categoryFeedback.bookId(), updatedCategoryFeedback.bookId());
            }
        }
    }

    @Override
    protected void notifyReviewers() {
        // Send message to reviewers that a plot has been verified.
        PlotMessage plotMessage = new PlotMessage("A plot has been verified, there %s %d %s awaiting verification.", true);
        Network.getInstance().getChat().sendSocketMesage(plotMessage);
    }
}
