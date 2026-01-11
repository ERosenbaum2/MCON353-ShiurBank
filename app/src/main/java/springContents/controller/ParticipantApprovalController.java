package springContents.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import springContents.dao.ParticipantApprovalDAO;
import springContents.dao.ShiurSeriesDAO;
import springContents.model.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for participant approval operations.
 * Handles participant application requests, approval, and rejection for series.
 */
@RestController
@RequestMapping("/api/series")
public class ParticipantApprovalController {

    private final ParticipantApprovalDAO participantApprovalDAO;
    private final ShiurSeriesDAO shiurSeriesDAO;

    /**
     * Constructs a new ParticipantApprovalController with the specified dependencies.
     *
     * @param participantApprovalDAO the ParticipantApprovalDAO for approval operations
     * @param shiurSeriesDAO the ShiurSeriesDAO for series operations
     */
    @Autowired
    public ParticipantApprovalController(ParticipantApprovalDAO participantApprovalDAO,
                                         ShiurSeriesDAO shiurSeriesDAO) {
        this.participantApprovalDAO = participantApprovalDAO;
        this.shiurSeriesDAO = shiurSeriesDAO;
    }

    /**
     * Get series information for application modal
     */
    @GetMapping("/{seriesId}/application-info")
    public ResponseEntity<Map<String, Object>> getApplicationInfo(
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
            // Get series details
            Map<String, Object> seriesInfo = participantApprovalDAO.getSeriesApplicationInfo(seriesId);
            if (seriesInfo == null) {
                response.put("success", false);
                response.put("message", "Series not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            // Get gabbai information
            List<Map<String, Object>> gabbaim = participantApprovalDAO.getGabbaiInfo(seriesId);

            // Check if user is already a participant
            boolean isParticipant = participantApprovalDAO.isParticipant(user.getUserId(), seriesId);

            // Check if user has a pending application
            boolean hasPending = participantApprovalDAO.hasPendingApplication(user.getUserId(), seriesId);

            response.put("success", true);
            response.put("seriesInfo", seriesInfo);
            response.put("gabbaim", gabbaim);
            response.put("isParticipant", isParticipant);
            response.put("hasPendingApplication", hasPending);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching application info: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Apply to join a series
     */
    @PostMapping("/{seriesId}/apply")
    @Transactional
    public ResponseEntity<Map<String, Object>> applyToSeries(
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
            // Check if series exists and get info
            Map<String, Object> seriesInfo = participantApprovalDAO.getSeriesApplicationInfo(seriesId);
            if (seriesInfo == null) {
                response.put("success", false);
                response.put("message", "Series not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            // Check if already a participant
            if (participantApprovalDAO.isParticipant(user.getUserId(), seriesId)) {
                response.put("success", false);
                response.put("message", "You are already a participant in this series");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Check if already has pending application
            if (participantApprovalDAO.hasPendingApplication(user.getUserId(), seriesId)) {
                response.put("success", false);
                response.put("message", "You already have a pending application for this series");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            boolean requiresPermission = (Boolean) seriesInfo.get("requiresPermission");

            if (!requiresPermission) {
                // Auto-approve: add directly to participants
                shiurSeriesDAO.addParticipant(user.getUserId(), seriesId);
                response.put("success", true);
                response.put("message", "You have been added to the series");
                response.put("autoApproved", true);
            } else {
                // Requires approval: add to pending table
                participantApprovalDAO.addPendingApproval(user.getUserId(), seriesId);
                response.put("success", true);
                response.put("message", "Your application has been submitted and is pending approval");
                response.put("autoApproved", false);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error submitting application: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get pending participants for a series (gabbai only)
     */
    @GetMapping("/{seriesId}/pending-participants")
    public ResponseEntity<Map<String, Object>> getPendingParticipants(
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
                response.put("message", "You are not authorized to view pending participants for this series");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            List<Map<String, Object>> pendingParticipants = participantApprovalDAO.getPendingParticipants(seriesId);

            response.put("success", true);
            response.put("pendingParticipants", pendingParticipants);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching pending participants: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Approve a pending participant (gabbai only)
     */
    @PostMapping("/{seriesId}/approve-participant")
    @Transactional
    public ResponseEntity<Map<String, Object>> approveParticipant(
            @PathVariable Long seriesId,
            @RequestBody Map<String, Object> body,
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
                response.put("message", "You are not authorized to approve participants for this series");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            Long participantUserId = toLong(body.get("userId"));
            if (participantUserId == null) {
                response.put("success", false);
                response.put("message", "User ID is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Check if there's a pending application
            if (!participantApprovalDAO.hasPendingApplication(participantUserId, seriesId)) {
                response.put("success", false);
                response.put("message", "No pending application found for this user");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            // Remove from pending table
            participantApprovalDAO.removePendingApproval(participantUserId, seriesId);

            // Add to participants table
            shiurSeriesDAO.addParticipant(participantUserId, seriesId);

            response.put("success", true);
            response.put("message", "Participant approved successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error approving participant: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Reject a pending participant (gabbai only)
     */
    @PostMapping("/{seriesId}/reject-participant")
    @Transactional
    public ResponseEntity<Map<String, Object>> rejectParticipant(
            @PathVariable Long seriesId,
            @RequestBody Map<String, Object> body,
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
                response.put("message", "You are not authorized to reject participants for this series");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            Long participantUserId = toLong(body.get("userId"));
            if (participantUserId == null) {
                response.put("success", false);
                response.put("message", "User ID is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Check if there's a pending application
            if (!participantApprovalDAO.hasPendingApplication(participantUserId, seriesId)) {
                response.put("success", false);
                response.put("message", "No pending application found for this user");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            // Remove from pending table (rejection = just remove, don't add to participants)
            participantApprovalDAO.removePendingApproval(participantUserId, seriesId);

            response.put("success", true);
            response.put("message", "Participant rejected successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error rejecting participant: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
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