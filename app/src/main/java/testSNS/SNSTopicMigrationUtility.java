package testSNS;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Standalone utility to migrate existing series to have SNS topics.
 * This checks for existing S3 buckets and creates corresponding SNS topics.
 * To run this utility:
 * 1. Ensure your database has been migrated with the sns_topic_arn column
 * 2. Update the CREDENTIALS_PATH if needed
 * 3. Run this class's main method directly
 */
public class SNSTopicMigrationUtility {

    private static final Logger logger = LoggerFactory.getLogger(SNSTopicMigrationUtility.class);

    // Update this path to point to your dbcredentials.properties file
    private static final String CREDENTIALS_PATH = "app/src/main/resources/dbcredentials.properties";

    private final Connection dbConnection;
    private final SnsClient snsClient;
    private final S3Client s3Client;

    public SNSTopicMigrationUtility() throws IOException, SQLException {
        // Load credentials
        Properties credentials = new Properties();
        credentials.load(new FileInputStream(CREDENTIALS_PATH));
        logger.info("Loaded credentials from {}", CREDENTIALS_PATH);

        // Setup database connection
        String endpoint = credentials.getProperty("db_connection");
        String database = credentials.getProperty("database");
        String username = credentials.getProperty("user");
        String password = credentials.getProperty("password");

        String connectionUrl = "jdbc:mysql://" + endpoint + "/" + database
                + "?useSSL=true"
                + "&serverTimezone=UTC";

        this.dbConnection = DriverManager.getConnection(connectionUrl, username, password);
        logger.info("Connected to database: {}", connectionUrl);

        // Setup SNS client
        String snsRegionStr = credentials.getProperty("sns.region", "us-east-1");
        Region snsRegion = Region.of(snsRegionStr);
        String snsProfile = credentials.getProperty("sns.aws.profile",
                credentials.getProperty("s3.aws.profile", "default"));

        this.snsClient = SnsClient.builder()
                .region(snsRegion)
                .credentialsProvider(ProfileCredentialsProvider.create(snsProfile))
                .build();
        logger.info("SNS client initialized with region: {} and profile: {}", snsRegion, snsProfile);

        // Setup S3 client
        String s3RegionStr = credentials.getProperty("s3.region", "us-east-1");
        Region s3Region = Region.of(s3RegionStr);
        String s3Profile = credentials.getProperty("s3.aws.profile", "default");

        this.s3Client = S3Client.builder()
                .region(s3Region)
                .credentialsProvider(ProfileCredentialsProvider.create(s3Profile))
                .build();
        logger.info("S3 client initialized with region: {} and profile: {}", s3Region, s3Profile);
    }

    public void runMigration() {
        logger.info("=== Starting SNS Topic Migration Utility ===");

        try {
            List<Long> seriesIds = getAllSeriesIds();
            logger.info("Found {} total series in database", seriesIds.size());

            int createdCount = 0;
            int skippedCount = 0;
            int errorCount = 0;

            int bucketsCreated = 0;

            for (Long seriesId : seriesIds) {
                try {
                    // Check if topic already exists
                    String existingTopicArn = getSeriesTopicArn(seriesId);
                    if (existingTopicArn != null && !existingTopicArn.trim().isEmpty()) {
                        logger.info("Series {} already has topic ARN: {}", seriesId, existingTopicArn);
                        skippedCount++;
                        continue;
                    }

                    // Check if S3 bucket exists, create if not
                    if (!doesS3BucketExist(seriesId)) {
                        logger.info("Series {} does not have an S3 bucket, creating one...", seriesId);
                        createS3Bucket(seriesId);
                        bucketsCreated++;
                    }

                    // Create SNS topic
                    logger.info("Creating SNS topic for series {}", seriesId);
                    String topicArn = createSeriesTopic(seriesId);

                    // Update database
                    updateSeriesTopicArn(seriesId, topicArn);

                    logger.info("✓ Successfully created and associated topic for series {}: {}",
                            seriesId, topicArn);
                    createdCount++;

                } catch (Exception e) {
                    logger.error("✗ Error processing series {}: {}", seriesId, e.getMessage(), e);
                    errorCount++;
                }
            }

            logger.info("=== Migration Complete ===");
            logger.info("Total series processed: {}", seriesIds.size());
            logger.info("S3 buckets created: {}", bucketsCreated);
            logger.info("Topics created: {}", createdCount);
            logger.info("Series skipped: {}", skippedCount);
            logger.info("Errors encountered: {}", errorCount);

        } catch (Exception e) {
            logger.error("Fatal error during migration: {}", e.getMessage(), e);
        }
    }

    private List<Long> getAllSeriesIds() throws SQLException {
        List<Long> seriesIds = new ArrayList<>();
        String sql = "SELECT series_id FROM shiur_series ORDER BY series_id";

        try (PreparedStatement stmt = dbConnection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                seriesIds.add(rs.getLong("series_id"));
            }
        }

        return seriesIds;
    }

    private String getSeriesTopicArn(Long seriesId) throws SQLException {
        String sql = "SELECT sns_topic_arn FROM shiur_series WHERE series_id = ?";

        try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
            stmt.setLong(1, seriesId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("sns_topic_arn");
                }
            }
        }

        return null;
    }

    private void updateSeriesTopicArn(Long seriesId, String topicArn) throws SQLException {
        String sql = "UPDATE shiur_series SET sns_topic_arn = ? WHERE series_id = ?";

        try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
            stmt.setString(1, topicArn);
            stmt.setLong(2, seriesId);

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Updating series topic ARN failed, no rows affected.");
            }
        }
    }

    private boolean doesS3BucketExist(Long seriesId) {
        String bucketName = "shiur-series-" + seriesId;

        try {
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();

            s3Client.headBucket(headBucketRequest);
            return true;

        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            logger.warn("Error checking bucket {}: {}", bucketName, e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warn("Unexpected error checking bucket {}: {}", bucketName, e.getMessage());
            return false;
        }
    }

    private String createSeriesTopic(Long seriesId) {
        String topicName = "series-" + seriesId + "-notifications";

        try {
            CreateTopicRequest request = CreateTopicRequest.builder()
                    .name(topicName)
                    .build();

            CreateTopicResponse response = snsClient.createTopic(request);
            return response.topicArn();

        } catch (SnsException e) {
            logger.error("Error creating SNS topic for series {}: {}", seriesId, e.getMessage(), e);
            throw new RuntimeException("Failed to create SNS topic for series " + seriesId, e);
        }
    }

    private void createS3Bucket(Long seriesId) {
        String bucketName = "shiur-series-" + seriesId;

        try {
            // Check if bucket already exists
            try {
                HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                        .bucket(bucketName)
                        .build();
                s3Client.headBucket(headBucketRequest);
                logger.info("Bucket {} already exists", bucketName);
                return;
            } catch (S3Exception e) {
                if (e.statusCode() != 404) {
                    throw e;
                }
                // Bucket doesn't exist, proceed with creation
            }

            // Create the bucket
            software.amazon.awssdk.services.s3.model.CreateBucketRequest createBucketRequest =
                    software.amazon.awssdk.services.s3.model.CreateBucketRequest.builder()
                            .bucket(bucketName)
                            .build();

            s3Client.createBucket(createBucketRequest);
            logger.info("✓ Successfully created S3 bucket: {}", bucketName);

        } catch (S3Exception e) {
            logger.error("Failed to create S3 bucket {}: {} - {}", bucketName,
                    e.awsErrorDetails().errorCode(), e.awsErrorDetails().errorMessage(), e);
            throw new RuntimeException("Failed to create S3 bucket for series " + seriesId + ": " +
                    e.awsErrorDetails().errorMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error creating S3 bucket {}: {}", bucketName, e.getMessage(), e);
            throw new RuntimeException("Failed to create S3 bucket for series " + seriesId, e);
        }
    }

    public void close() {
        try {
            if (dbConnection != null && !dbConnection.isClosed()) {
                dbConnection.close();
                logger.info("Database connection closed");
            }
        } catch (SQLException e) {
            logger.error("Error closing database connection", e);
        }

        if (snsClient != null) {
            snsClient.close();
            logger.info("SNS client closed");
        }

        if (s3Client != null) {
            s3Client.close();
            logger.info("S3 client closed");
        }
    }

    public static void main(String[] args) {
        SNSTopicMigrationUtility utility = null;

        try {
            utility = new SNSTopicMigrationUtility();
            utility.runMigration();

        } catch (IOException e) {
            System.err.println("Error loading credentials file: " + e.getMessage());
            System.err.println("Make sure the file exists at: " + CREDENTIALS_PATH);
            System.err.println("You may need to update the CREDENTIALS_PATH constant in this class.");
            e.printStackTrace();

        } catch (SQLException e) {
            System.err.println("Database connection error: " + e.getMessage());
            e.printStackTrace();

        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();

        } finally {
            if (utility != null) {
                utility.close();
            }
        }
    }
}