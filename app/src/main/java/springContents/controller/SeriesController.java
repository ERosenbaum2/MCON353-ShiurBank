package springContents.controller;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
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

/**
 * REST controller for shiur series management.
 * Handles series creation, retrieval, deletion, and related operations including
 * S3 bucket and SNS topic management.
 */
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

    /**
     * Constructs a new SeriesController with the specified dependencies.
     *
     * @param topicDAO the TopicDAO for topic operations
     * @param rebbiDAO the RebbiDAO for Rebbi operations
     * @param shiurSeriesDAO the ShiurSeriesDAO for series operations
     * @param userDAO the UserDAO for user operations
     * @param adminDAO the AdminDAO for admin operations
     * @param snsService the SNSService for SNS operations
     * @param s3Service the S3Service for S3 operations
     */
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

    /**
     * Retrieves all topics from the database.
     *
     * @return a list of all topics
     */
    @GetMapping("/topics")
    public List<Topic> getTopics() {
        return topicDAO.getAllTopics();
    }

    /**
     * Retrieves all Rebbeim from the database.
     *
     * @return a list of all Rebbeim
     */
    @GetMapping("/rebbeim")
    public List<Rebbi> getRebbeim() {
        return rebbiDAO.getAllRebbeim();
    }

    /**
     * Retrieves all series where the current user is a gabbai or participant.
     *
     * @param session the HTTP session for authentication
     * @return a list of series maps with role information, or UNAUTHORIZED if not logged in
     */
    @GetMapping("/my-series")
    public ResponseEntity<List<Map<String, Object>>> getMySeries(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<Map<String, Object>> series = shiurSeriesDAO.getAllSeriesForUser(user.getUserId());
        return ResponseEntity.ok(series);
    }

    /**
     * Creates a new shiur series with S3 bucket and SNS topic setup.
     *
     * @param body a map containing series information including rebbiId, topicId, instId,
     *             description, requiresPermission, and optionally extraGabbaim
     * @param session the HTTP session for authentication
     * @return a response map with success status, seriesId, and needsVerification flag
     * @throws RuntimeException if series creation fails or S3/SNS setup fails
     */
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

        // Create SNS topic for the series
        try {
            String topicArn = snsService.createSeriesTopic(seriesId);
            shiurSeriesDAO.updateSeriesTopicArn(seriesId, topicArn);
            logger.info("Created and associated SNS topic for series {}: {}", seriesId, topicArn);
        } catch (Exception e) {
            logger.error("Failed to create SNS topic for series {}: {}", seriesId, e.getMessage(), e);
            // Re-throw to trigger transaction rollback
            throw new RuntimeException("Failed to create SNS topic for series: " + e.getMessage(), e);
        }

        // Creator is always a gabbai
        shiurSeriesDAO.addGabbai(current.getUserId(), seriesId);
        // Creator is also automatically a participant
        shiurSeriesDAO.addParticipant(current.getUserId(), seriesId);

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
                // Extra gabbaim are also automatically participants
                shiurSeriesDAO.addParticipant(extra.getUserId(), seriesId);
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
                    // The series is already created and added to series_pending_approval
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

    /**
     * Retrieves detailed information about a specific series.
     *
     * @param id the series ID
     * @param session the HTTP session for authentication
     * @return a map containing series details, or NOT_FOUND if series doesn't exist,
     *         or UNAUTHORIZED if not logged in
     */
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

    /**
     * Checks if the current user is a gabbai for the specified series.
     *
     * @param id the series ID
     * @param session the HTTP session for authentication
     * @return a map with isGabbai boolean, or UNAUTHORIZED if not logged in
     */
    @GetMapping("/series/{id}/is-gabbai")
    public ResponseEntity<Map<String, Boolean>> checkIfGabbai(@PathVariable("id") Long id,
                                                              HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean isGabbai = shiurSeriesDAO.isGabbaiForSeries(user.getUserId(), id);
        Map<String, Boolean> response = new HashMap<>();
        response.put("isGabbai", isGabbai);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a series and its associated S3 bucket and SNS topic.
     *
     * @param id the series ID to delete
     * @param session the HTTP session for authentication
     * @return a response map with success status and message
     * @throws RuntimeException if deletion fails
     */
    @DeleteMapping("/series/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteSeries(@PathVariable("id") Long id,
                                                            HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            resp.put("success", false);
            resp.put("message", "Not logged in.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
        }

        // Verify user is a gabbai for this series
        if (!shiurSeriesDAO.isGabbaiForSeries(user.getUserId(), id)) {
            resp.put("success", false);
            resp.put("message", "You do not have permission to delete this series.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(resp);
        }

        try {
            // Get the topic ARN before deleting the series
            String topicArn = shiurSeriesDAO.getSeriesTopicArn(id);

            // Delete the series (this will CASCADE delete related records)
            shiurSeriesDAO.deleteSeries(id);
            logger.info("Deleted series {} by user {}", id, user.getUserId());

            // Delete the SNS topic if it exists
            if (topicArn != null && !topicArn.trim().isEmpty()) {
                try {
                    snsService.deleteTopic(topicArn);
                    logger.info("Deleted SNS topic for series {}: {}", id, topicArn);
                } catch (Exception e) {
                    logger.error("Failed to delete SNS topic for series {}, but series was deleted",
                            id, e);
                    // Don't fail the operation if SNS deletion fails
                }
            }

            // Delete S3 bucket if needed
            try {
                s3Service.deleteSeriesBucket(id);
                logger.info("Deleted S3 bucket for series {}", id);
            } catch (Exception e) {
                logger.error("Failed to delete S3 bucket for series {}, but series was deleted",
                        id, e);
                // Don't fail the operation if S3 deletion fails
            }

            resp.put("success", true);
            resp.put("message", "Series deleted successfully.");
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            logger.error("Error deleting series {}: {}", id, e.getMessage(), e);
            resp.put("success", false);
            resp.put("message", "An error occurred while deleting the series.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    /**
     * Converts an object to a Long value.
     *
     * @param value the object to convert
     * @return the Long value, or null if conversion fails
     */
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