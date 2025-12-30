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
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

@Service
public class SNSService {
    private static final Logger logger = LoggerFactory.getLogger(SNSService.class);
    private final SnsClient snsClient;
    private final String topicArn;
    private final Region region;

    @Autowired
    public SNSService(ResourceLoader resourceLoader) throws IOException {
        Properties credentials = new Properties();

        Resource resource = resourceLoader.getResource("classpath:dbcredentials.properties");
        credentials.load(resource.getInputStream());

        this.topicArn = credentials.getProperty("sns.topic.arn", "");
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

        logger.info("SNSService initialized with topic ARN: {} in region: {} using profile: {}",
                topicArn, region, profileName);
    }

    /**
     * Send a notification to the SNS topic
     * @param subject The subject of the message
     * @param message The message body
     */
    public void publishNotification(String subject, String message) {
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
            logger.info("SNS notification published successfully. MessageId: {}", response.messageId());
        } catch (SnsException e) {
            logger.error("Error publishing SNS notification: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to publish SNS notification", e);
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
}