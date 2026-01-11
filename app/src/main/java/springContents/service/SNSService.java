package springContents.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Service for managing AWS SNS (Simple Notification Service) operations.
 * Handles creation and deletion of SNS topics for series notifications,
 * publishing notifications, and managing subscriptions.
 */
@Service
public class SNSService {
    private static final Logger logger = LoggerFactory.getLogger(SNSService.class);
    private final SnsClient snsClient;
    private final String adminTopicArn;
    private final Region region;

    /**
     * Constructs a new SNSService with configuration from dbcredentials.properties.
     *
     * @param resourceLoader the resource loader to access configuration files
     * @throws IOException if the properties file cannot be read
     */
    @Autowired
    public SNSService(ResourceLoader resourceLoader) throws IOException {
        Properties credentials = new Properties();

        Resource resource = resourceLoader.getResource("classpath:dbcredentials.properties");
        credentials.load(resource.getInputStream());

        this.adminTopicArn = credentials.getProperty("sns.topic.arn", "");
        String regionStr = credentials.getProperty("sns.region", "us-east-1");
        this.region = Region.of(regionStr);

        String profileName = credentials.getProperty("sns.aws.profile", "default");
        // If not specified, fall back to S3 profile
        if (profileName.equals("default") && credentials.getProperty("s3.aws.profile") != null) {
            profileName = credentials.getProperty("s3.aws.profile");
        }

        this.snsClient = SnsClient.builder()
                .region(region)
                .credentialsProvider(ProfileCredentialsProvider.create(profileName))
                .build();

        logger.info("SNSService initialized with admin topic ARN: {} in region: {} using profile: {}",
                adminTopicArn, region, profileName);
    }

    /**
     * Send a notification to the admin SNS topic
     * @param subject The subject of the message
     * @param message The message body
     */
    public void publishNotification(String subject, String message) {
        publishToTopic(adminTopicArn, subject, message);
    }

    /**
     * Send a notification to a specific topic
     * @param topicArn The ARN of the topic
     * @param subject The subject of the message
     * @param message The message body
     */
    public void publishToTopic(String topicArn, String subject, String message) {
        if (topicArn == null || topicArn.trim().isEmpty()) {
            logger.warn("SNS topic ARN not configured, skipping notification");
            return;
        }

        try {
            PublishRequest request = PublishRequest.builder()
                    .topicArn(topicArn)
                    .subject(subject)
                    .message(message)
                    .build();

            PublishResponse response = snsClient.publish(request);
            logger.info("SNS notification published successfully to {}. MessageId: {}",
                    topicArn, response.messageId());
        } catch (SnsException e) {
            logger.error("Error publishing SNS notification to {}: {}", topicArn, e.getMessage(), e);
            throw new RuntimeException("Failed to publish SNS notification", e);
        }
    }

    /**
     * Create a new SNS topic for a series
     * @param seriesId The series ID
     * @return The ARN of the created topic
     */
    public String createSeriesTopic(Long seriesId) {
        String topicName = "series-" + seriesId + "-notifications";

        try {
            CreateTopicRequest request = CreateTopicRequest.builder()
                    .name(topicName)
                    .build();

            CreateTopicResponse response = snsClient.createTopic(request);
            String topicArn = response.topicArn();

            logger.info("Created SNS topic for series {}: {}", seriesId, topicArn);
            return topicArn;
        } catch (SnsException e) {
            logger.error("Error creating SNS topic for series {}: {}", seriesId, e.getMessage(), e);
            throw new RuntimeException("Failed to create SNS topic for series " + seriesId, e);
        }
    }

    /**
     * Delete an SNS topic
     * @param topicArn The ARN of the topic to delete
     */
    public void deleteTopic(String topicArn) {
        if (topicArn == null || topicArn.trim().isEmpty()) {
            logger.warn("Cannot delete topic: ARN is null or empty");
            return;
        }

        try {
            DeleteTopicRequest request = DeleteTopicRequest.builder()
                    .topicArn(topicArn)
                    .build();

            snsClient.deleteTopic(request);
            logger.info("Deleted SNS topic: {}", topicArn);
        } catch (SnsException e) {
            logger.error("Error deleting SNS topic {}: {}", topicArn, e.getMessage(), e);
            throw new RuntimeException("Failed to delete SNS topic: " + topicArn, e);
        }
    }

    /**
     * Subscribe an email address to a topic
     * @param topicArn The ARN of the topic
     * @param emailAddress The email address to subscribe
     * @return The subscription ARN (will be "pending confirmation" initially)
     */
    public String subscribeEmail(String topicArn, String emailAddress) {
        if (topicArn == null || topicArn.trim().isEmpty()) {
            throw new IllegalArgumentException("Topic ARN cannot be null or empty");
        }
        if (emailAddress == null || emailAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address cannot be null or empty");
        }

        try {
            SubscribeRequest request = SubscribeRequest.builder()
                    .topicArn(topicArn)
                    .protocol("email")
                    .endpoint(emailAddress)
                    .build();

            SubscribeResponse response = snsClient.subscribe(request);
            String subscriptionArn = response.subscriptionArn();

            logger.info("Subscribed {} to topic {}. Subscription ARN: {}",
                    emailAddress, topicArn, subscriptionArn);
            return subscriptionArn;
        } catch (SnsException e) {
            logger.error("Error subscribing {} to topic {}: {}",
                    emailAddress, topicArn, e.getMessage(), e);
            throw new RuntimeException("Failed to subscribe email to topic", e);
        }
    }

    /**
     * Unsubscribe from a topic
     * @param subscriptionArn The subscription ARN
     */
    public void unsubscribe(String subscriptionArn) {
        if (subscriptionArn == null || subscriptionArn.trim().isEmpty()) {
            logger.warn("Cannot unsubscribe: subscription ARN is null or empty");
            return;
        }

        // Don't try to unsubscribe pending confirmations
        if ("pending confirmation".equalsIgnoreCase(subscriptionArn)) {
            logger.info("Skipping unsubscribe for pending confirmation");
            return;
        }

        try {
            UnsubscribeRequest request = UnsubscribeRequest.builder()
                    .subscriptionArn(subscriptionArn)
                    .build();

            snsClient.unsubscribe(request);
            logger.info("Unsubscribed: {}", subscriptionArn);
        } catch (SnsException e) {
            logger.error("Error unsubscribing {}: {}", subscriptionArn, e.getMessage(), e);
            throw new RuntimeException("Failed to unsubscribe: " + subscriptionArn, e);
        }
    }

    /**
     * Get all subscriptions for a topic
     * @param topicArn The ARN of the topic
     * @return List of subscriptions with endpoint and subscription ARN
     */
    public List<Map<String, String>> listSubscriptionsByTopic(String topicArn) {
        List<Map<String, String>> subscriptions = new ArrayList<>();

        try {
            ListSubscriptionsByTopicRequest request = ListSubscriptionsByTopicRequest.builder()
                    .topicArn(topicArn)
                    .build();

            ListSubscriptionsByTopicResponse response = snsClient.listSubscriptionsByTopic(request);

            for (software.amazon.awssdk.services.sns.model.Subscription sub : response.subscriptions()) {
                Map<String, String> subscription = new HashMap<>();
                subscription.put("endpoint", sub.endpoint());
                subscription.put("subscriptionArn", sub.subscriptionArn());
                subscription.put("protocol", sub.protocol());
                subscriptions.add(subscription);
            }

            logger.debug("Found {} subscriptions for topic {}", subscriptions.size(), topicArn);

        } catch (SnsException e) {
            logger.error("Error listing subscriptions for topic {}: {}", topicArn, e.getMessage(), e);
            throw new RuntimeException("Failed to list subscriptions for topic: " + topicArn, e);
        }

        return subscriptions;
    }

    /**
     * Find subscription ARN by email address for a topic
     * @param topicArn The ARN of the topic
     * @param email The email address to search for
     * @return The subscription ARN, or null if not found or still pending
     */
    public String findSubscriptionArnByEmail(String topicArn, String email) {
        try {
            List<Map<String, String>> subscriptions = listSubscriptionsByTopic(topicArn);

            for (Map<String, String> sub : subscriptions) {
                String endpoint = sub.get("endpoint");
                String subArn = sub.get("subscriptionArn");

                // Match email (case-insensitive) and check if confirmed
                if (endpoint != null && endpoint.equalsIgnoreCase(email)) {
                    // If ARN is "PendingConfirmation", return null
                    if ("PendingConfirmation".equalsIgnoreCase(subArn)) {
                        logger.debug("Subscription for {} is still pending confirmation", email);
                        return null;
                    }
                    logger.debug("Found confirmed subscription ARN for {}: {}", email, subArn);
                    return subArn;
                }
            }

            logger.debug("No subscription found for email: {}", email);
            return null;

        } catch (Exception e) {
            logger.error("Error finding subscription ARN for email {}: {}", email, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Send a notification about a new series requiring verification
     * @param seriesId The series ID
     * @param seriesDescription The series description
     * @param rebbiName The Rabbi's name
     * @param topicName The topic name
     * @param institutionName The institution name
     * @param creatorUsername The username of the creator
     */
    public void notifyNewSeriesRequiringVerification(Long seriesId, String seriesDescription,
                                                     String rebbiName, String topicName,
                                                     String institutionName, String creatorUsername) {
        String subject = "New Series Requires Verification - Series #" + seriesId;
        String message = String.format(
                """
                A new series has been created and requires verification:
                
                Series ID: %d
                Description: %s
                Rabbi: %s
                Topic: %s
                Institution: %s
                Created by: %s
                
                Please review and verify this series in the admin panel.""",
                seriesId, seriesDescription, rebbiName, topicName, institutionName, creatorUsername
        );

        publishNotification(subject, message);
    }

    /**
     * Send notification about a new recording to series subscribers
     * @param topicArn The series topic ARN
     * @param recordingTitle The title of the new recording
     * @param rebbiName The Rabbi's name
     * @param topicName The topic name
     * @param seriesDescription The series description
     * @param recordedAt The recording date
     */
    public void notifyNewRecording(String topicArn, String recordingTitle,
                                   String rebbiName, String topicName,
                                   String seriesDescription, String recordedAt) {
        String subject = "New Shiur Uploaded - " + recordingTitle;
        String message = String.format(
                """
                A new Shiur has been uploaded to this series:
                
                Title: %s
                Rabbi: %s
                Topic: %s
                Series: %s
                Recorded: %s
                
                Log in to ShiurBank to listen to this shiur.""",
                recordingTitle, rebbiName, topicName, seriesDescription, recordedAt
        );

        publishToTopic(topicArn, subject, message);
    }
}