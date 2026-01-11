package springContents.controller;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import springContents.dao.ShiurSeriesDAO;
import springContents.dao.SubscriberDAO;
import springContents.dao.UserDAO;
import springContents.model.User;
import springContents.service.SNSService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for SNS subscription management.
 * Handles subscription creation, removal, and management for series notifications.
 */
@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionController.class);

    private final SubscriberDAO subscriberDAO;
    private final ShiurSeriesDAO shiurSeriesDAO;
    private final UserDAO userDAO;
    private final SNSService snsService;

    /**
     * Constructs a new SubscriptionController with the specified dependencies.
     *
     * @param subscriberDAO the SubscriberDAO for subscription operations
     * @param shiurSeriesDAO the ShiurSeriesDAO for series operations
     * @param userDAO the UserDAO for user operations
     * @param snsService the SNSService for SNS operations
     */
    @Autowired
    public SubscriptionController(SubscriberDAO subscriberDAO,
                                  ShiurSeriesDAO shiurSeriesDAO,
                                  UserDAO userDAO,
                                  SNSService snsService) {
        this.subscriberDAO = subscriberDAO;
        this.shiurSeriesDAO = shiurSeriesDAO;
        this.userDAO = userDAO;
        this.snsService = snsService;
    }

    /**
     * Get all subscriber types
     */
    @GetMapping("/types")
    public ResponseEntity<Map<String, Object>> getSubscriberTypes(HttpSession session) {
        Map<String, Object> response = new HashMap<>();

        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                response.put("success", false);
                response.put("message", "Not logged in.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            List<Map<String, Object>> types = subscriberDAO.getAllSubscriberTypes();

            response.put("success", true);
            response.put("types", types);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error fetching subscriber types: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error fetching subscription types.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Check if user is subscribed to a series
     */
    @GetMapping("/series/{seriesId}/status")
    public ResponseEntity<Map<String, Object>> getSubscriptionStatus(@PathVariable Long seriesId,
                                                                     HttpSession session) {
        Map<String, Object> response = new HashMap<>();

        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                response.put("success", false);
                response.put("message", "Not logged in.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            Map<String, Object> subscription = subscriberDAO.getUserSubscription(user.getUserId(), seriesId);

            boolean isSubscribed = false;
            boolean isPending = false;

            if (subscription != null) {
                String arn = (String) subscription.get("snsSubscriptionArn");
                // Check if subscription is confirmed (ARN is not "pending confirmation")
                if (arn != null && !arn.equalsIgnoreCase("pending confirmation")) {
                    isSubscribed = true;
                } else {
                    isPending = true;
                }
            }

            response.put("success", true);
            response.put("isSubscribed", isSubscribed);
            response.put("isPending", isPending);
            if (subscription != null) {
                response.put("subscription", subscription);
            }
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error checking subscription status for series {}: {}", seriesId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error checking subscription status.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Subscribe user to a series
     */
    @PostMapping("/series/{seriesId}/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(@PathVariable Long seriesId,
                                                         @RequestBody Map<String, Object> body,
                                                         HttpSession session) {
        Map<String, Object> response = new HashMap<>();

        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                response.put("success", false);
                response.put("message", "Not logged in.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // Get subscription type ID from request
            Long subscriptionTypeId = toLong(body.get("subscriptionTypeId"));
            if (subscriptionTypeId == null) {
                response.put("success", false);
                response.put("message", "Subscription type is required.");
                return ResponseEntity.badRequest().body(response);
            }

            // Check if already subscribed
            if (subscriberDAO.isUserSubscribed(user.getUserId(), seriesId, subscriptionTypeId)) {
                response.put("success", false);
                response.put("message", "You are already subscribed to this series.");
                return ResponseEntity.badRequest().body(response);
            }

            // Get series topic ARN
            String topicArn = shiurSeriesDAO.getSeriesTopicArn(seriesId);
            if (topicArn == null || topicArn.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "This series does not have notifications enabled.");
                return ResponseEntity.badRequest().body(response);
            }

            // Get user's email
            String email = user.getEmail();
            if (email == null || email.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Your account does not have an email address.");
                return ResponseEntity.badRequest().body(response);
            }

            // Subscribe to SNS topic
            String subscriptionArn;
            try {
                subscriptionArn = snsService.subscribeEmail(topicArn, email);
                logger.info("Subscribed user {} to series {} SNS topic. Subscription ARN: {}",
                        user.getUserId(), seriesId, subscriptionArn);
            } catch (Exception e) {
                logger.error("Failed to subscribe user {} to SNS topic for series {}: {}",
                        user.getUserId(), seriesId, e.getMessage(), e);
                response.put("success", false);
                response.put("message", "Failed to subscribe to notifications. Please try again.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            // Add subscription to database with pending ARN
            // Note: subscriptionArn will be "pending confirmation" until user confirms via email
            long subscriberId = subscriberDAO.addSubscription(
                    user.getUserId(), seriesId, subscriptionTypeId, subscriptionArn);

            logger.info("User {} subscribed to series {} with type {} (pending confirmation)",
                    user.getUserId(), seriesId, subscriptionTypeId);

            response.put("success", true);
            response.put("subscriberId", subscriberId);
            response.put("isPending", true);
            response.put("message", "Subscription pending! Please check your email to confirm the subscription.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error subscribing user to series {}: {}", seriesId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "An error occurred while subscribing. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Unsubscribe user from a series
     */
    @PostMapping("/series/{seriesId}/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribe(@PathVariable Long seriesId,
                                                           HttpSession session) {
        Map<String, Object> response = new HashMap<>();

        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                response.put("success", false);
                response.put("message", "Not logged in.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // Get user's subscription
            Map<String, Object> subscription = subscriberDAO.getUserSubscription(user.getUserId(), seriesId);
            if (subscription == null) {
                response.put("success", false);
                response.put("message", "You are not subscribed to this series.");
                return ResponseEntity.badRequest().body(response);
            }

            String snsSubscriptionArn = (String) subscription.get("snsSubscriptionArn");

            // Unsubscribe from SNS topic
            if (snsSubscriptionArn != null && !snsSubscriptionArn.trim().isEmpty()) {
                try {
                    snsService.unsubscribe(snsSubscriptionArn);
                    logger.info("Unsubscribed user {} from series {} SNS topic", user.getUserId(), seriesId);
                } catch (Exception e) {
                    logger.error("Failed to unsubscribe user {} from SNS topic for series {}: {}",
                            user.getUserId(), seriesId, e.getMessage(), e);
                    // Continue with database removal even if SNS unsubscribe fails
                }
            }

            // Remove subscription from database
            subscriberDAO.removeSubscription(user.getUserId(), seriesId);

            logger.info("User {} unsubscribed from series {}", user.getUserId(), seriesId);

            response.put("success", true);
            response.put("message", "Successfully unsubscribed from this series.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error unsubscribing user from series {}: {}", seriesId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "An error occurred while unsubscribing. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Check and sync subscription status with AWS SNS
     * This checks if the user's email subscription has been confirmed in AWS
     * and updates the database accordingly
     */
    @PostMapping("/series/{seriesId}/sync-status")
    public ResponseEntity<Map<String, Object>> syncSubscriptionStatus(@PathVariable Long seriesId,
                                                                      HttpSession session) {
        Map<String, Object> response = new HashMap<>();

        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                response.put("success", false);
                response.put("message", "Not logged in.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // Get user's subscription from database
            Map<String, Object> subscription = subscriberDAO.getUserSubscription(user.getUserId(), seriesId);
            if (subscription == null) {
                response.put("success", true);
                response.put("isSubscribed", false);
                response.put("isPending", false);
                return ResponseEntity.ok(response);
            }

            String currentArn = (String) subscription.get("snsSubscriptionArn");

            // If not pending, no need to sync
            if (currentArn != null && !currentArn.equalsIgnoreCase("pending confirmation")) {
                response.put("success", true);
                response.put("isSubscribed", true);
                response.put("isPending", false);
                return ResponseEntity.ok(response);
            }

            // Get series topic ARN
            String topicArn = shiurSeriesDAO.getSeriesTopicArn(seriesId);
            if (topicArn == null || topicArn.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Series topic not found.");
                return ResponseEntity.badRequest().body(response);
            }

            // Check AWS SNS for confirmed subscription
            String confirmedArn = snsService.findSubscriptionArnByEmail(topicArn, user.getEmail());

            if (confirmedArn != null) {
                // Update database with confirmed ARN
                subscriberDAO.updateSubscriptionArn(user.getUserId(), seriesId, confirmedArn);
                logger.info("Updated subscription ARN for user {} on series {} to confirmed ARN",
                        user.getUserId(), seriesId);

                response.put("success", true);
                response.put("isSubscribed", true);
                response.put("isPending", false);
                response.put("message", "Subscription confirmed!");
            } else {
                // Still pending
                response.put("success", true);
                response.put("isSubscribed", false);
                response.put("isPending", true);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error syncing subscription status for series {}: {}", seriesId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error syncing subscription status.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}