package springContents.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

@Service
public class SNSService {
    private static final Logger logger = LoggerFactory.getLogger(SNSService.class);
    private final SnsClient snsClient;
    private final Region region;
    private final String adminTopicName;
    private final String subscriberTopicName;
    private final List<String> adminEmails;
    private String adminTopicArn;
    private String subscriberTopicArn;

    @Autowired
    public SNSService(ResourceLoader resourceLoader) throws IOException {
        Properties credentials = new Properties();

        Resource resource = resourceLoader.getResource("classpath:dbcredentials.properties");
        credentials.load(resource.getInputStream());

        String regionStr = credentials.getProperty("sns.region", "us-east-1");
        this.region = Region.of(regionStr);
        this.adminTopicName = credentials.getProperty("sns.admin.topic.name", "shiurbank-admin-notifications");
        this.subscriberTopicName = credentials.getProperty("sns.subscriber.topic.name", "shiurbank-subscriber-notifications");

        String profileName = credentials.getProperty("s3.aws.profile", "default");

        this.snsClient = SnsClient.builder()
                .region(region)
                .credentialsProvider(ProfileCredentialsProvider.create(profileName))
                .build();

        // Parse admin emails from properties
        String adminEmailsStr = credentials.getProperty("sns.admin.emails", "");
        if (adminEmailsStr != null && !adminEmailsStr.trim().isEmpty()) {
            String[] emails = adminEmailsStr.split(",");
            this.adminEmails = new ArrayList<>();
            for (String email : emails) {
                String trimmed = email.trim();
                if (!trimmed.isEmpty()) {
                    this.adminEmails.add(trimmed);
                }
            }
        } else {
            this.adminEmails = new ArrayList<>();
        }

        logger.info("SNSService initialized with region: {} using profile: {}", region, profileName);
        logger.info("Admin topic name: {}, Subscriber topic name: {}", adminTopicName, subscriberTopicName);
        logger.info("Admin emails configured: {}", adminEmails.size());
    }

    @PostConstruct
    public void initializeTopics() {
        try {
            // Create or get admin topic
            this.adminTopicArn = createOrGetTopic(adminTopicName);
            logger.info("Admin topic ARN: {}", adminTopicArn);

            // Create or get subscriber topic
            this.subscriberTopicArn = createOrGetTopic(subscriberTopicName);
            logger.info("Subscriber topic ARN: {}", subscriberTopicArn);

            // Subscribe admin emails to admin topic
            for (String email : adminEmails) {
                if (email != null && !email.trim().isEmpty()) {
                    try {
                        subscribeEmail(adminTopicArn, email.trim());
                        logger.info("Subscribed admin email to admin topic: {}", email.trim());
                    } catch (Exception e) {
                        logger.warn("Failed to subscribe admin email {}: {}", email.trim(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error initializing SNS topics: {}", e.getMessage(), e);
        }
    }

    private String createOrGetTopic(String topicName) {
        try {
            // Try to create the topic
            CreateTopicRequest createRequest = CreateTopicRequest.builder()
                    .name(topicName)
                    .build();

            CreateTopicResponse createResponse = snsClient.createTopic(createRequest);
            logger.info("Created SNS topic: {} with ARN: {}", topicName, createResponse.topicArn());
            return createResponse.topicArn();
        } catch (SnsException e) {
            if (e.awsErrorDetails().errorCode().equals("ResourceConflictException") ||
                e.awsErrorDetails().errorCode().equals("TopicLimitExceeded")) {
                // Topic might already exist, try to get it
                logger.info("Topic {} may already exist, attempting to get ARN", topicName);
                try {
                    ListTopicsRequest listRequest = ListTopicsRequest.builder().build();
                    ListTopicsResponse listResponse = snsClient.listTopics(listRequest);
                    
                    for (Topic topic : listResponse.topics()) {
                        String arn = topic.topicArn();
                        if (arn.endsWith(":" + topicName)) {
                            logger.info("Found existing topic: {} with ARN: {}", topicName, arn);
                            return arn;
                        }
                    }
                    // If not found, throw original exception
                    throw new RuntimeException("Topic " + topicName + " not found and could not be created", e);
                } catch (Exception listEx) {
                    throw new RuntimeException("Error listing topics to find existing topic", listEx);
                }
            } else {
                throw new RuntimeException("Error creating SNS topic: " + topicName, e);
            }
        }
    }

    public void subscribeEmail(String topicArn, String email) {
        try {
            SubscribeRequest subscribeRequest = SubscribeRequest.builder()
                    .topicArn(topicArn)
                    .protocol("email")
                    .endpoint(email)
                    .build();

            SubscribeResponse response = snsClient.subscribe(subscribeRequest);
            logger.info("Subscribed email {} to topic {} with subscription ARN: {}", 
                    email, topicArn, response.subscriptionArn());
        } catch (SnsException e) {
            String errorCode = e.awsErrorDetails().errorCode();
            if (errorCode.equals("InvalidParameter") || 
                errorCode.equals("SubscriptionLimitExceeded") ||
                e.getMessage().contains("already subscribed") ||
                e.getMessage().contains("already exists")) {
                // Already subscribed or invalid - log and continue
                logger.debug("Email {} may already be subscribed to topic {}: {}", email, topicArn, e.getMessage());
            } else {
                logger.error("Error subscribing email {} to topic {}: {}", email, topicArn, e.getMessage(), e);
                throw new RuntimeException("Failed to subscribe email to topic", e);
            }
        }
    }

    public void publishToAdminTopic(String message, String subject) {
        if (adminTopicArn == null) {
            logger.warn("Admin topic ARN is null, cannot publish message");
            return;
        }
        publishMessage(adminTopicArn, message, subject);
    }

    public void publishToSubscriberTopic(String message, String subject) {
        if (subscriberTopicArn == null) {
            logger.warn("Subscriber topic ARN is null, cannot publish message");
            return;
        }
        publishMessage(subscriberTopicArn, message, subject);
    }

    private void publishMessage(String topicArn, String message, String subject) {
        try {
            PublishRequest publishRequest = PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(message)
                    .subject(subject)
                    .build();

            PublishResponse response = snsClient.publish(publishRequest);
            logger.info("Published message to topic {} with message ID: {}", topicArn, response.messageId());
        } catch (SnsException e) {
            logger.error("Error publishing message to topic {}: {}", topicArn, e.getMessage(), e);
            throw new RuntimeException("Failed to publish message to topic", e);
        }
    }

    public String getAdminTopicArn() {
        return adminTopicArn;
    }

    public String getSubscriberTopicArn() {
        return subscriberTopicArn;
    }

    public List<String> getAdminEmails() {
        return new ArrayList<>(adminEmails);
    }
}

