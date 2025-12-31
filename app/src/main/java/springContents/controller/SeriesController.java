package springContents.controller;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import springContents.dao.AdminDAO;
import springContents.dao.RebbiDAO;
import springContents.dao.ShiurSeriesDAO;
import springContents.dao.TopicDAO;
import springContents.dao.UserDAO;
import springContents.model.Rebbi;
import springContents.model.Topic;
import springContents.model.User;
import springContents.service.SNSService;
import springContents.service.S3Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SeriesController {

    private static final Logger logger = LoggerFactory.getLogger(SeriesController.class);

    private final TopicDAO topicDAO;
    private final RebbiDAO rebbiDAO;
    private final ShiurSeriesDAO shiurSeriesDAO;
    private final UserDAO userDAO;
    private final AdminDAO adminDAO;
    private final SNSService snsService;
    private final S3Service s3Service;

    @Autowired
    public SeriesController(TopicDAO topicDAO,
                            RebbiDAO rebbiDAO,
                            ShiurSeriesDAO shiurSeriesDAO,
                            UserDAO userDAO,
                            AdminDAO adminDAO,
                            SNSService snsService,
                            S3Service s3Service) {
        this.topicDAO = topicDAO;
        this.rebbiDAO = rebbiDAO;
        this.shiurSeriesDAO = shiurSeriesDAO;
        this.userDAO = userDAO;
        this.adminDAO = adminDAO;
        this.snsService = snsService;
        this.s3Service = s3Service;
    }

    @GetMapping("/topics")
    public List<Topic> getTopics() {
        return topicDAO.getAllTopics();
    }

    @GetMapping("/rebbeim")
    public List<Rebbi> getRebbeim() {
        return rebbiDAO.getAllRebbeim();
    }

    @GetMapping("/my-series")
    public ResponseEntity<List<Map<String, Object>>> getMySeries(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<Map<String, Object>> series = shiurSeriesDAO.getSeriesForGabbai(user.getUserId());
        return ResponseEntity.ok(series);
    }

    @PostMapping("/series")
    @Transactional
    public ResponseEntity<Map<String, Object>> createSeries(@RequestBody Map<String, Object> body,
                                                            HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        User current = (User) session.getAttribute("user");
        if (current == null) {
            resp.put("success", false);
            resp.put("message", "Not logged in.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
        }

        Long rebbiId = toLong(body.get("rebbiId"));
        Long topicId = toLong(body.get("topicId"));
        Long instId = toLong(body.get("instId"));
        String description = (String) body.get("description");
        boolean requiresPermission = body.get("requiresPermission") instanceof Boolean
                ? (Boolean) body.get("requiresPermission")
                : Boolean.FALSE;

        if (rebbiId == null || topicId == null || instId == null ||
                description == null || description.trim().isEmpty()) {
            resp.put("success", false);
            resp.put("message", "Missing required fields.");
            return ResponseEntity.badRequest().body(resp);
        }

        // Check if verification is required BEFORE adding gabbai
        // Verification is required if the creator is NOT already a gabbai for another series from the same Rabbi
        boolean needsVerification = !shiurSeriesDAO.isGabbaiForSameRebbi(current.getUserId(), rebbiId);

        long seriesId = shiurSeriesDAO.createSeries(rebbiId, topicId, requiresPermission, instId, description);

        // Create S3 bucket for the series
        // This must happen within the transaction so it rolls back if bucket creation fails
        try {
            String bucketName = s3Service.createSeriesBucket(seriesId);
            logger.info("Created S3 bucket {} for series {}", bucketName, seriesId);
        } catch (Exception e) {
            logger.error("Failed to create S3 bucket for series {}: {}", seriesId, e.getMessage(), e);
            // Re-throw to trigger transaction rollback
            throw new RuntimeException("Failed to create S3 bucket for series: " + e.getMessage(), e);
        }

        // Creator is always a gabbai
        shiurSeriesDAO.addGabbai(current.getUserId(), seriesId);

        // Optional extra gabbaim: if any invalid, fail the whole creation
        Object extraGabbaimObj = body.get("extraGabbaim");
        if (extraGabbaimObj instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<Object> extraList = (List<Object>) extraGabbaimObj;
            for (Object entry : extraList) {
                if (!(entry instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> gabbaiData = (Map<String, Object>) entry;
                String extraUsername = gabbaiData.get("username") != null
                        ? gabbaiData.get("username").toString()
                        : null;
                String extraPassword = gabbaiData.get("password") != null
                        ? gabbaiData.get("password").toString()
                        : null;

                if (extraUsername == null || extraUsername.trim().isEmpty()) {
                    continue;
                }
                if (extraPassword == null || extraPassword.isEmpty()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Each additional gabbai must have both a username and a password."
                    );
                }

                User extra = userDAO.authenticateUser(extraUsername, extraPassword);
                if (extra == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Additional gabbai credentials are invalid."
                    );
                }
                shiurSeriesDAO.addGabbai(extra.getUserId(), seriesId);
            }
        }

        if (needsVerification) {
            // Add to pending permission table
            adminDAO.addPendingPermission(seriesId);

            // Get series details for notification
            Map<String, Object> seriesDetails = shiurSeriesDAO.getSeriesDetails(seriesId);
            if (seriesDetails != null) {
                // Send SNS notification
                try {
                    snsService.notifyNewSeriesRequiringVerification(
                            seriesId,
                            description,
                            (String) seriesDetails.get("rebbiName"),
                            (String) seriesDetails.get("topicName"),
                            (String) seriesDetails.get("institutionName"),
                            current.getUsername()
                    );
                } catch (Exception e) {
                    // Log error but don't fail the series creation
                    // The series is already created and added to pending_permission
                    // SNS notification failure shouldn't block the operation
                    logger.error("Failed to send SNS notification for series {}: {}", seriesId, e.getMessage(), e);
                }
            }
        }

        resp.put("success", true);
        resp.put("seriesId", seriesId);
        resp.put("needsVerification", needsVerification);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/series/{id}")
    public ResponseEntity<Map<String, Object>> getSeries(@PathVariable("id") Long id,
                                                         HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> details = shiurSeriesDAO.getSeriesDetails(id);
        if (details == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(details);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}