package springContents.controller;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import springContents.dao.RecordingDAO;
import springContents.dao.ShiurSeriesDAO;
import springContents.model.User;
import springContents.service.S3Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RecordingController {

    private static final Logger logger = LoggerFactory.getLogger(RecordingController.class);
    private static final long MAX_FILE_SIZE = 1024L * 1024L * 1024L; // 1GB in bytes

    private final RecordingDAO recordingDAO;
    private final ShiurSeriesDAO shiurSeriesDAO;
    private final S3Service s3Service;

    @Autowired
    public RecordingController(RecordingDAO recordingDAO,
                               ShiurSeriesDAO shiurSeriesDAO,
                               S3Service s3Service) {
        this.recordingDAO = recordingDAO;
        this.shiurSeriesDAO = shiurSeriesDAO;
        this.s3Service = s3Service;
    }

    @GetMapping("/series/{seriesId}/recordings")
    public ResponseEntity<Map<String, Object>> getRecordings(
            @PathVariable Long seriesId,
            @RequestParam(value = "sort", defaultValue = "newest") String sortOrder,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Check authentication
            User user = (User) session.getAttribute("user");
            if (user == null) {
                response.put("success", false);
                response.put("message", "Not logged in.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // Get recordings
            List<Map<String, Object>> recordings = recordingDAO.getRecordingsForSeries(seriesId, sortOrder);

            response.put("success", true);
            response.put("recordings", recordings);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error fetching recordings for series {}: {}", seriesId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Failed to fetch recordings.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/series/{seriesId}/recordings")
    @Transactional
    public ResponseEntity<Map<String, Object>> uploadRecording(
            @PathVariable Long seriesId,
            @RequestParam("title") String title,
            @RequestParam("recordedAt") String recordedAtStr,
            @RequestParam("keyword1") String keyword1,
            @RequestParam("keyword2") String keyword2,
            @RequestParam("keyword3") String keyword3,
            @RequestParam("keyword4") String keyword4,
            @RequestParam("keyword5") String keyword5,
            @RequestParam("keyword6") String keyword6,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("audioFile") MultipartFile audioFile,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Check authentication
            User user = (User) session.getAttribute("user");
            if (user == null) {
                response.put("success", false);
                response.put("message", "Not logged in.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // Check if user is a gabbai for this series
            if (!shiurSeriesDAO.isGabbaiForSeries(user.getUserId(), seriesId)) {
                response.put("success", false);
                response.put("message", "You do not have permission to upload to this series.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            // Validate file
            if (audioFile.isEmpty()) {
                response.put("success", false);
                response.put("message", "No file provided.");
                return ResponseEntity.badRequest().body(response);
            }

            // Check file size
            if (audioFile.getSize() > MAX_FILE_SIZE) {
                response.put("success", false);
                response.put("message", "File size exceeds 1GB limit.");
                return ResponseEntity.badRequest().body(response);
            }

            // Validate file type
            String originalFilename = audioFile.getOriginalFilename();
            if (originalFilename == null || !isValidAudioFile(originalFilename)) {
                response.put("success", false);
                response.put("message", "Invalid file type. Please upload an audio file.");
                return ResponseEntity.badRequest().body(response);
            }

            // Get file extension
            String fileExtension = getFileExtension(originalFilename);

            // Parse recorded date/time
            LocalDateTime recordedAt;
            try {
                recordedAt = LocalDateTime.parse(recordedAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException e) {
                response.put("success", false);
                response.put("message", "Invalid date/time format.");
                return ResponseEntity.badRequest().body(response);
            }

            // Validate required fields
            if (title == null || title.trim().isEmpty() ||
                    keyword1 == null || keyword1.trim().isEmpty() ||
                    keyword2 == null || keyword2.trim().isEmpty() ||
                    keyword3 == null || keyword3.trim().isEmpty() ||
                    keyword4 == null || keyword4.trim().isEmpty() ||
                    keyword5 == null || keyword5.trim().isEmpty() ||
                    keyword6 == null || keyword6.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "All fields except description are required.");
                return ResponseEntity.badRequest().body(response);
            }

            // Create recording in database (get ID first for S3 path)
            long recordingId = recordingDAO.createRecording(
                    seriesId,
                    "temp", // Temporary placeholder, will be updated
                    recordedAt,
                    title.trim(),
                    keyword1.trim(),
                    keyword2.trim(),
                    keyword3.trim(),
                    keyword4.trim(),
                    keyword5.trim(),
                    keyword6.trim(),
                    description != null ? description.trim() : null
            );

            // Upload file to S3
            String s3FilePath;
            try {
                s3FilePath = s3Service.uploadAudioFile(seriesId, recordingId, audioFile, fileExtension);
            } catch (Exception e) {
                logger.error("Failed to upload file to S3 for recording {}: {}", recordingId, e.getMessage(), e);
                throw new RuntimeException("Failed to upload audio file to S3: " + e.getMessage(), e);
            }

            // Update recording with actual S3 path
            recordingDAO.updateS3FilePath(recordingId, s3FilePath);

            logger.info("Successfully created recording {} for series {} by user {}",
                    recordingId, seriesId, user.getUserId());

            response.put("success", true);
            response.put("recordingId", recordingId);
            response.put("message", "Shiur uploaded successfully!");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error uploading recording for series {}: {}", seriesId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "An error occurred while uploading the shiur. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Check if the file is a valid audio file based on extension
     */
    private boolean isValidAudioFile(String filename) {
        String lowerFilename = filename.toLowerCase();
        return lowerFilename.endsWith(".mp3") ||
                lowerFilename.endsWith(".wav") ||
                lowerFilename.endsWith(".ogg") ||
                lowerFilename.endsWith(".m4a") ||
                lowerFilename.endsWith(".opus") ||
                lowerFilename.endsWith(".flac") ||
                lowerFilename.endsWith(".aac") ||
                lowerFilename.endsWith(".webm") ||
                lowerFilename.endsWith(".aiff") ||
                lowerFilename.endsWith(".aif") ||
                lowerFilename.endsWith(".wma");
    }

    /**
     * Extract file extension from filename
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        return "mp3"; // Default to mp3 if no extension found
    }
}