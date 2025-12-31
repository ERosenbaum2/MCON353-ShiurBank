package springContents.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springContents.dao.RecordingDAO;
import springContents.dao.ShiurSeriesDAO;
import springContents.model.User;
import springContents.service.SNSService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recordings")
public class RecordingController {

    private final RecordingDAO recordingDAO;
    private final ShiurSeriesDAO shiurSeriesDAO;
    private final SNSService snsService;

    @Autowired
    public RecordingController(RecordingDAO recordingDAO,
                              ShiurSeriesDAO shiurSeriesDAO,
                              SNSService snsService) {
        this.recordingDAO = recordingDAO;
        this.shiurSeriesDAO = shiurSeriesDAO;
        this.snsService = snsService;
    }

//    @PostMapping
//    @Transactional
//    public ResponseEntity<Map<String, Object>> createRecording(@RequestBody Map<String, Object> body,
//                                                              HttpSession session) {
//        Map<String, Object> resp = new HashMap<>();
//        User current = (User) session.getAttribute("user");
//        if (current == null) {
//            resp.put("success", false);
//            resp.put("message", "Not logged in.");
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
//        }
//
//        // Parse request body
//        Long seriesId = toLong(body.get("seriesId"));
//        String s3FilePath = (String) body.get("s3FilePath");
//        String recordedAtStr = (String) body.get("recordedAt");
//        String keyword1 = (String) body.get("keyword1");
//        String keyword2 = (String) body.get("keyword2");
//        String keyword3 = (String) body.get("keyword3");
//        String keyword4 = (String) body.get("keyword4");
//        String keyword5 = (String) body.get("keyword5");
//        String keyword6 = (String) body.get("keyword6");
//        String description = (String) body.get("description");
//
//        // Validate required fields
//        if (seriesId == null || s3FilePath == null || s3FilePath.trim().isEmpty() ||
//            recordedAtStr == null || recordedAtStr.trim().isEmpty() ||
//            keyword1 == null || keyword1.trim().isEmpty() ||
//            keyword2 == null || keyword2.trim().isEmpty() ||
//            keyword3 == null || keyword3.trim().isEmpty() ||
//            keyword4 == null || keyword4.trim().isEmpty() ||
//            keyword5 == null || keyword5.trim().isEmpty() ||
//            keyword6 == null || keyword6.trim().isEmpty()) {
//            resp.put("success", false);
//            resp.put("message", "Missing required fields.");
//            return ResponseEntity.badRequest().body(resp);
//        }
//
//        // Parse recordedAt
//        LocalDateTime recordedAt;
//        try {
//            // Try ISO format first
//            recordedAt = LocalDateTime.parse(recordedAtStr);
//        } catch (DateTimeParseException e) {
//            try {
//                // Try common date formats
//                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//                recordedAt = LocalDateTime.parse(recordedAtStr, formatter);
//            } catch (DateTimeParseException e2) {
//                resp.put("success", false);
//                resp.put("message", "Invalid date format for recordedAt. Use ISO format or yyyy-MM-dd HH:mm:ss");
//                return ResponseEntity.badRequest().body(resp);
//            }
//        }
//
//        // Create recording
//        long recordingId;
//        try {
//            recordingId = recordingDAO.createRecording(
//                    seriesId,
//                    s3FilePath,
//                    recordedAt,
//                    keyword1,
//                    keyword2,
//                    keyword3,
//                    keyword4,
//                    keyword5,
//                    keyword6,
//                    description
//            );
//        } catch (Exception e) {
//            resp.put("success", false);
//            resp.put("message", "Error creating recording: " + e.getMessage());
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
//        }
//
//        // Get series details and subscribers
//        try {
//            Map<String, Object> seriesDetails = shiurSeriesDAO.getSeriesDetails(seriesId);
//            List<String> subscriberEmails = shiurSeriesDAO.getSubscriberEmailsForSeries(seriesId);
//
//            if (seriesDetails != null && !subscriberEmails.isEmpty()) {
//                // Subscribe each subscriber email to the subscriber topic (if not already subscribed)
//                // Note: SNS will handle duplicate subscriptions gracefully
//                for (String email : subscriberEmails) {
//                    try {
//                        snsService.subscribeEmail(snsService.getSubscriberTopicArn(), email);
//                    } catch (Exception e) {
//                        // Log but continue - subscription might already exist
//                        System.err.println("Warning: Could not subscribe email " + email + ": " + e.getMessage());
//                    }
//                }
//
//                // Publish notification to subscriber topic
//                String subject = "New Recording Available";
//                String message = String.format(
//                    "A new recording has been uploaded to a series you're subscribed to:\n\n" +
//                    "Series: %s\n" +
//                    "Topic: %s\n" +
//                    "Rebbi: %s\n" +
//                    "Recording ID: %d\n" +
//                    "S3 File Path: %s\n" +
//                    "Recorded At: %s\n" +
//                    "%s\n" +
//                    "Keywords: %s, %s, %s, %s, %s, %s",
//                    seriesDetails.get("description"),
//                    seriesDetails.get("topicName"),
//                    seriesDetails.get("rebbiName"),
//                    recordingId,
//                    s3FilePath,
//                    recordedAt.toString(),
//                    description != null ? "Description: " + description + "\n" : "",
//                    keyword1, keyword2, keyword3, keyword4, keyword5, keyword6
//                );
//                snsService.publishToSubscriberTopic(message, subject);
//            }
//        } catch (Exception e) {
//            // Log error but don't fail the request
//            // Notification failure shouldn't prevent recording creation
//            System.err.println("Failed to send subscriber notification: " + e.getMessage());
//        }
//
//        resp.put("success", true);
//        resp.put("recordingId", recordingId);
//        return ResponseEntity.ok(resp);
//    }
//
//    private Long toLong(Object value) {
//        if (value == null) {
//            return null;
//        }
//        if (value instanceof Number) {
//            return ((Number) value).longValue();
//        }
//        try {
//            return Long.parseLong(value.toString());
//        } catch (NumberFormatException ex) {
//            return null;
//        }
//    }
}