package springContents.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;

import java.io.IOException;
import java.io.InputStream;
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
     * Create a new S3 bucket for a series
     * @param seriesId The series ID to use for bucket naming
     * @return The bucket name that was created
     * @throws RuntimeException if bucket creation fails
     */
    public String createSeriesBucket(Long seriesId) {
        String bucketName = "shiur-series-" + seriesId;

        try {
            // Check if bucket already exists
            try {
                HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                        .bucket(bucketName)
                        .build();
                s3Client.headBucket(headBucketRequest);
                logger.warn("Bucket {} already exists", bucketName);
                return bucketName;
            } catch (S3Exception e) {
                if (e.statusCode() != 404) {
                    throw e;
                }
                // Bucket doesn't exist, proceed with creation
            }

            // Create the bucket
            CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build();

            s3Client.createBucket(createBucketRequest);
            logger.info("Successfully created S3 bucket: {}", bucketName);

            return bucketName;
        } catch (S3Exception e) {
            logger.error("Failed to create S3 bucket {}: {} - {}", bucketName, e.awsErrorDetails().errorCode(), e.awsErrorDetails().errorMessage(), e);
            throw new RuntimeException("Failed to create S3 bucket for series " + seriesId + ": " + e.awsErrorDetails().errorMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error creating S3 bucket {}: {}", bucketName, e.getMessage(), e);
            throw new RuntimeException("Failed to create S3 bucket for series " + seriesId, e);
        }
    }

    /**
     * Upload an audio file to the series bucket
     * @param seriesId The series ID
     * @param recordingId The recording ID to use in the file path
     * @param file The audio file to upload
     * @param fileExtension The file extension (e.g., "mp3", "wav")
     * @return The S3 file path (key) of the uploaded file
     * @throws RuntimeException if upload fails
     */
    public String uploadAudioFile(Long seriesId, Long recordingId, MultipartFile file, String fileExtension) {
        String bucketName = "shiur-series-" + seriesId;
        String key = recordingId + "." + fileExtension;

        try (InputStream inputStream = file.getInputStream()) {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, file.getSize()));
            logger.info("Successfully uploaded file to S3: {}/{}", bucketName, key);

            return key;
        } catch (IOException e) {
            logger.error("Error reading file for upload: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read file for upload", e);
        } catch (S3Exception e) {
            logger.error("Failed to upload to S3 {}/{}: {} - {}", bucketName, key,
                    e.awsErrorDetails().errorCode(), e.awsErrorDetails().errorMessage(), e);
            throw new RuntimeException("Failed to upload file to S3: " + e.awsErrorDetails().errorMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error uploading to S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    /**
     * Get audio file from a series bucket as InputStream
     * @param seriesId The series ID
     * @param fileName The file name/key in the bucket
     * @return ResponseInputStream containing the audio file
     */
    public ResponseInputStream<GetObjectResponse> getAudioFileFromSeriesBucket(Long seriesId, String fileName) {
        String bucketName = "shiur-series-" + seriesId;

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            return s3Client.getObject(getObjectRequest);
        } catch (Exception e) {
            logger.error("Error getting audio file {} from series bucket {}: {}", fileName, bucketName, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve audio file: " + fileName, e);
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