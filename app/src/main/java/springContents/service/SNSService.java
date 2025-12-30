package springContents.service;

import jakarta.annotation.PostConstruct;
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
import java.util.List;
import java.util.Properties;

@Service
public class SNSService {

    private static final Logger logger = LoggerFactory.getLogger(SNSService.class);

    private final SnsClient snsClient;
    private final Region region;

    // Topic configuration
    private final String adminTopicName;
    private final String subscriberTopicName;
    private final String configuredAdminTopicArn;

    // Runtime-resolved ARNs
    private String adminTopicArn;
    private String subscriberTopicArn;

    private final List<String> adminEmails;

    @Autowired
    public SNSService(ResourceLoader resourceLoader) throws IOException {
        Properties props = new Properties();
        Resource resource = resourceLoader.getResource("classpath:dbcredentials.properties");
        props.load(resource.getInputStream());

        // Region
        this.region = Region.of(props.getProperty("sns.region", "us-east-1"));

        // Topics
        this.adminTopicName = props.getProperty(
                "sns.admin.topic.name",
                "shiurbank-admin-notifications"
        );
        this.subscriberTopicName = props.getProperty(
                "sns.subscriber.topic.name",
                "shiurbank-subscriber-notifications"
        );
        this.configuredAdminTopicArn = props.getProperty("sns.admin.topic.arn");

        // Profile selection
        String profileName = props.getProperty("sns.aws.profile");
        if (profileName == null || profileName.isBlank()) {
            profileName = props.getProperty("s3.aws.profile", "default");
        }

        // Admin emails
        this.adminEmails = new ArrayList<>();
        String emails = props.getProperty("sns.admin.emails", "");
        for (String email : emails.split(",")) {
            if (!email.trim().isEmpty()) {
                adminEmails.add(email.trim());
            }
        }

        this.snsClient = SnsClient.builder()
                .region(region)
                .credentialsProvider(ProfileCredentialsProvider.create(profileName))
                .build();

        logger.info(
                "SNSService initialized (region={}, profile={}, adminEmails={})",
                region, profileName, adminEmails.size()
        );
    }

    @PostConstruct
    public void initializeTopics() {
        try {
            // Admin topic: use ARN if explicitly configured
            if (configuredAdminTopicArn != null && !configuredAdminTopicArn.isBlank()) {
                this.adminTopicArn = configuredAdminTopicArn;
                logger.info("Using configured admin topic ARN: {}", adminTopicArn);
            } else {
                this.adminTopicArn = createOrGetTopic(adminTopicName);
            }

            // Subscriber topic is always name-based
            this.subscriberTopicArn = createOrGetTopic(subscriberTopicName);

            // Subscribe admin emails
            for (String email : adminEmails) {
                subscribeEmail(adminTopicArn, email);
            }

        } catch (Exception e) {
            logger.error("Failed to initialize SNS topics", e);
        }
    }

    private String createOrGetTopic(String topicName) {
        try {
            CreateTopicResponse response = snsClient.createTopic(
                    CreateTopicRequest.builder().name(topicName).build()
            );
            logger.info("SNS topic ready: {} ({})", topicName, response.topicArn());
            return response.topicArn();
        } catch (SnsException e) {
            throw new RuntimeException("Unable to create/get SNS topic: " + topicName, e);
        }
    }

    private void subscribeEmail(String topicArn, String email) {
        try {
            snsClient.subscribe(
                    SubscribeRequest.builder()
                            .topicArn(topicArn)
                            .protocol("email")
                            .endpoint(email)
                            .build()
            );
            logger.info("Subscription request sent for {}", email);
        } catch (SnsException e) {
            logger.debug("Email {} may already be subscribed: {}", email, e.getMessage());
        }
    }

    /* =======================
       Publishing APIs
       ======================= */

    public void publishToAdminTopic(String subject, String message) {
        publish(adminTopicArn, subject, message);
    }

    public void publishToSubscriberTopic(String subject, String message) {
        publish(subscriberTopicArn, subject, message);
    }

    private void publish(String topicArn, String subject, String message) {
        if (topicArn == null) {
            logger.warn("SNS topic ARN is null, skipping publish");
            return;
        }

        try {
            PublishResponse response = snsClient.publish(
                    PublishRequest.builder()
                            .topicArn(topicArn)
                            .subject(subject)
                            .message(message)
                            .build()
            );
            logger.info("SNS message published: {}", response.messageId());
        } catch (SnsException e) {
            throw new RuntimeException("SNS publish failed", e);
        }
    }

    /* =======================
       Domain-specific helper
       ======================= */

    public void notifyNewSeriesRequiringVerification(
            Long seriesId,
            String seriesDescription,
            String rebbiName,
            String topicName,
            String institutionName,
            String creatorUsername
    ) {
        String subject = "New Series Requires Verification - Series #" + seriesId;
        String message = String.format("""
                A new series has been created and requires verification:

                Series ID: %d
                Description: %s
                Rabbi: %s
                Topic: %s
                Institution: %s
                Created by: %s

                Please review this series in the admin panel.
                """,
                seriesId,
                seriesDescription,
                rebbiName,
                topicName,
                institutionName,
                creatorUsername
        );

        publishToAdminTopic(subject, message);
    }

    /* =======================
       Accessors
       ======================= */

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
