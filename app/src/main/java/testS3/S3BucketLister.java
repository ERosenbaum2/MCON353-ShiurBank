package testS3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

import java.io.InputStream;
import java.time.Instant;
import java.util.Properties;

/**
 * Utility class to list all S3 buckets for verification purposes.
 * Run this as a standalone Java application to verify S3 setup.
 */
public class S3BucketLister {
    private static final Logger logger = LoggerFactory.getLogger(S3BucketLister.class);

    public static void main(String[] args) {
        S3Client s3Client = null;

        try {
            // Load properties from dbcredentials.properties
            Properties credentials = new Properties();
            ClassPathResource resource = new ClassPathResource("dbcredentials.properties");

            try (InputStream inputStream = resource.getInputStream()) {
                credentials.load(inputStream);
            }

            String regionStr = credentials.getProperty("s3.region", "us-east-1");
            String profileName = credentials.getProperty("s3.aws.profile", "default");
            Region region = Region.of(regionStr);

            System.out.println("=".repeat(60));
            System.out.println("S3 Bucket Lister");
            System.out.println("=".repeat(60));
            System.out.println("AWS Profile: " + profileName);
            System.out.println("Region: " + region);
            System.out.println("=".repeat(60));
            System.out.println();

            // Create S3 client
            s3Client = S3Client.builder()
                    .region(region)
                    .credentialsProvider(ProfileCredentialsProvider.create(profileName))
                    .build();

            // List all buckets
            ListBucketsResponse listBucketsResponse = s3Client.listBuckets();

            if (listBucketsResponse.buckets().isEmpty()) {
                System.out.println("No buckets found.");
            } else {
                System.out.println("Found " + listBucketsResponse.buckets().size() + " bucket(s):");
                System.out.println();

                int count = 1;
                for (Bucket bucket : listBucketsResponse.buckets()) {
                    String bucketName = bucket.name();
                    Instant creationDate = bucket.creationDate();

                    System.out.println(count + ". " + bucketName);
                    System.out.println("   Created: " + creationDate);

                    // Count objects in bucket
                    try {
                        int objectCount = countObjectsInBucket(s3Client, bucketName);
                        System.out.println("   Objects: " + objectCount);
                    } catch (Exception e) {
                        System.out.println("   Objects: Unable to count (Error: " + e.getMessage() + ")");
                        logger.warn("Failed to count objects in bucket: " + bucketName, e);
                    }

                    // Highlight series buckets
                    if (bucketName.startsWith("shiur-series-")) {
                        System.out.println("   >>> This is a series bucket! <<<");
                    }

                    System.out.println();
                    count++;
                }
            }

            System.out.println("=".repeat(60));
            System.out.println("✓ S3 connection successful!");
            System.out.println("=".repeat(60));

        } catch (Exception e) {
            System.err.println("=".repeat(60));
            System.err.println("✗ Error connecting to S3:");
            System.err.println("=".repeat(60));
            System.err.println("Error Type: " + e.getClass().getSimpleName());
            System.err.println("Message: " + e.getMessage());
            System.err.println();
            System.err.println("Common issues:");
            System.err.println("1. Check that AWS credentials are configured correctly");
            System.err.println("2. Verify the profile name in dbcredentials.properties");
            System.err.println("3. Ensure AWS CLI is configured: aws configure --profile " +
                    "shiurbank-s3-user");
            System.err.println("4. Check that the IAM user has s3:ListAllMyBuckets permission");
            System.err.println("=".repeat(60));

            logger.error("Failed to list S3 buckets", e);
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (s3Client != null) {
                s3Client.close();
            }
        }
    }

    /**
     * Counts the total number of objects in a bucket.
     * Handles pagination for buckets with more than 1000 objects.
     *
     * @param s3Client The S3 client
     * @param bucketName The name of the bucket
     * @return The total number of objects in the bucket
     */
    private static int countObjectsInBucket(S3Client s3Client, String bucketName) {
        int totalCount = 0;
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(1000);

            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken);
            }

            ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());
            totalCount += response.keyCount();
            continuationToken = response.nextContinuationToken();

        } while (continuationToken != null);

        return totalCount;
    }
}