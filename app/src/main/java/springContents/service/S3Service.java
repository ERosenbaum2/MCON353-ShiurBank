package springContents.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Service
public class S3Service {
    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);
    private final S3Client s3Client;
    private final String bucketName;
    private final Region region;
    private final String prefix;

    @Autowired
    public S3Service(ResourceLoader resourceLoader) throws IOException {
        Properties credentials = new Properties();

        Resource resource = resourceLoader.getResource("classpath:dbcredentials.properties");
        credentials.load(resource.getInputStream());

        this.bucketName = credentials.getProperty("s3.bucket.name", "your-bucket-name");
        String regionStr = credentials.getProperty("s3.region", "us-east-1");
        this.region = Region.of(regionStr);
        this.prefix = credentials.getProperty("s3.prefix", "");

        String profileName = credentials.getProperty("s3.aws.profile", "default");

        this.s3Client = S3Client.builder()
                .region(region)
                .credentialsProvider(ProfileCredentialsProvider.create(profileName))
                .build();

        if (prefix != null && !prefix.isEmpty()) {
            logger.info("S3Service initialized with bucket: {} in region: {} using profile: {} with prefix: {}",
                    bucketName, region, profileName, prefix);
        } else {
            logger.info("S3Service initialized with bucket: {} in region: {} using profile: {}",
                    bucketName, region, profileName);
        }
    }

    /**
     * List all audio files in the S3 bucket
     */
    public List<String> listAudioFiles() {
        List<String> audioFiles = new ArrayList<>();
        try {
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName);

            // Add prefix if configured
            if (prefix != null && !prefix.isEmpty()) {
                requestBuilder.prefix(prefix);
            }

            ListObjectsV2Request listRequest = requestBuilder.build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

            for (S3Object s3Object : listResponse.contents()) {
                String key = s3Object.key();
                // Filter for audio files
                if (key.toLowerCase().endsWith(".mp3") ||
                        key.toLowerCase().endsWith(".wav") ||
                        key.toLowerCase().endsWith(".ogg") ||
                        key.toLowerCase().endsWith(".m4a") ||
                        key.toLowerCase().endsWith(".opus") ||
                        key.toLowerCase().endsWith(".flac") ||
                        key.toLowerCase().endsWith(".aac") ||
                        key.toLowerCase().endsWith(".webm") ||
                        key.toLowerCase().endsWith(".aiff") ||
                        key.toLowerCase().endsWith(".aif") ||
                        key.toLowerCase().endsWith(".wma")) {
                    audioFiles.add(key);
                }
            }

            logger.info("Found {} audio files in bucket", audioFiles.size());
        } catch (Exception e) {
            logger.error("Error listing audio files from S3: {}", e.getMessage(), e);
        }
        return audioFiles;
    }

    /**
     * Get audio file as InputStream
     */
    public ResponseInputStream<GetObjectResponse> getAudioFile(String key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.getObject(getObjectRequest);
        } catch (Exception e) {
            logger.error("Error getting audio file {} from S3: {}", key, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve audio file: " + key, e);
        }
    }
}