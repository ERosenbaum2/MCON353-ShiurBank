package springContents.controller;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import springContents.dao.ParticipantManagementDAO;
import springContents.dao.ShiurSeriesDAO;
import springContents.model.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing participants in shiur series
 * Handles viewing, removing, and promoting participants to gabbaim
 * All endpoints require gabbai authorization
 */
@RestController
@RequestMapping("/api/series")
public class ParticipantManagementController {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantManagementController.class);

    private final ParticipantManagementDAO participantManagementDAO;

    private final ShiurSeriesDAO shiurSeriesDAO;

    @Autowired
    public ParticipantManagementController(ParticipantManagementDAO participantManagementDAO, ShiurSeriesDAO shiurSeriesDAO) {
        this.participantManagementDAO = participantManagementDAO;
        this.shiurSeriesDAO = shiurSeriesDAO;
    }

    /**
     * Get all participants for a series (gabbai only)
     * Returns participants sorted alphabetically by last name
     */
    @GetMapping("/{seriesId}/participants")
    public ResponseEntity<Map<String, Object>> getParticipants(
            @PathVariable Long seriesId,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");

        if (user == null) {
            response.put("success", false);
            response.put("message", "User not authenticated");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            // Check if user is gabbai for this series
            if (!shiurSeriesDAO.isGabbaiForSeries(user.getUserId(), seriesId)) {
                response.put("success", false);
                response.put("message", "You must be a gabbai to view participants");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            List<Map<String, Object>> participants = participantManagementDAO.getParticipants(seriesId);

            response.put("success", true);
            response.put("participants", participants);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error fetching participants for series {}: {}", seriesId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error fetching participants: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Remove a participant from a series (gabbai only)
     * Removes from all associated tables: gabbaim, shiur_participants, subscribers, favorite_shiurim
     * Cannot remove yourself
     */
    @PostMapping("/{seriesId}/remove-participant")
    @Transactional
    public ResponseEntity<Map<String, Object>> removeParticipant(
            @PathVariable Long seriesId,
            @RequestBody Map<String, Object> body,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();
        User currentUser = (User) session.getAttribute("user");

        if (currentUser == null) {
            response.put("success", false);
            response.put("message", "User not authenticated");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            // Check if user is gabbai for this series
            if (!shiurSeriesDAO.isGabbaiForSeries(currentUser.getUserId(), seriesId)) {
                response.put("success", false);
                response.put("message", "You must be a gabbai to remove participants");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            Long userIdToRemove = toLong(body.get("userId"));
            if (userIdToRemove == null) {
                response.put("success", false);
                response.put("message", "User ID is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Prevent removing yourself
            if (userIdToRemove.equals(currentUser.getUserId())) {
                response.put("success", false);
                response.put("message", "You cannot remove yourself from the series");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Check if the user is actually a participant
            if (!participantManagementDAO.isParticipantInSeries(userIdToRemove, seriesId)) {
                response.put("success", false);
                response.put("message", "User is not a participant in this series");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Remove participant from all associated tables
            participantManagementDAO.removeParticipantFromSeries(userIdToRemove, seriesId);

            logger.info("User {} removed participant {} from series {}",
                    currentUser.getUserId(), userIdToRemove, seriesId);

            response.put("success", true);
            response.put("message", "Participant removed successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error removing participant {} from series {}: {}",
                    body.get("userId"), seriesId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error removing participant: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Add a participant as an additional gabbai (gabbai only)
     * User must already be a participant and not already a gabbai
     * User remains in shiur_participants table
     */
    @PostMapping("/{seriesId}/add-gabbai")
    @Transactional
    public ResponseEntity<Map<String, Object>> addParticipantAsGabbai(
            @PathVariable Long seriesId,
            @RequestBody Map<String, Object> body,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();
        User currentUser = (User) session.getAttribute("user");

        if (currentUser == null) {
            response.put("success", false);
            response.put("message", "User not authenticated");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            // Check if current user is gabbai for this series
            if (!shiurSeriesDAO.isGabbaiForSeries(currentUser.getUserId(), seriesId)) {
                response.put("success", false);
                response.put("message", "You must be a gabbai to add additional gabbaim");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            Long userIdToPromote = toLong(body.get("userId"));
            if (userIdToPromote == null) {
                response.put("success", false);
                response.put("message", "User ID is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Check if the user is a participant
            if (!participantManagementDAO.isParticipantInSeries(userIdToPromote, seriesId)) {
                response.put("success", false);
                response.put("message", "User must be a participant before being added as a gabbai");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Check if user is already a gabbai
            if (shiurSeriesDAO.isGabbaiForSeries(userIdToPromote, seriesId)) {
                response.put("success", false);
                response.put("message", "User is already a gabbai for this series");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Add user as gabbai (they remain as participant too)
            shiurSeriesDAO.addGabbai(userIdToPromote, seriesId);

            logger.info("User {} added participant {} as gabbai for series {}",
                    currentUser.getUserId(), userIdToPromote, seriesId);

            response.put("success", true);
            response.put("message", "User added as gabbai successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error adding participant {} as gabbai for series {}: {}",
                    body.get("userId"), seriesId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error adding gabbai: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Helper method to convert Object to Long
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