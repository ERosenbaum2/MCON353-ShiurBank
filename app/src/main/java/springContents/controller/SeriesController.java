package springContents.controller;

import jakarta.servlet.http.HttpSession;
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
import springContents.dao.RebbiDAO;
import springContents.dao.ShiurSeriesDAO;
import springContents.dao.TopicDAO;
import springContents.dao.UserDAO;
import springContents.model.Rebbi;
import springContents.model.Topic;
import springContents.model.User;
import springContents.service.SNSService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SeriesController {

    private final TopicDAO topicDAO;
    private final RebbiDAO rebbiDAO;
    private final ShiurSeriesDAO shiurSeriesDAO;
    private final UserDAO userDAO;
    private final SNSService snsService;

    @Autowired
    public SeriesController(TopicDAO topicDAO,
                            RebbiDAO rebbiDAO,
                            ShiurSeriesDAO shiurSeriesDAO,
                            UserDAO userDAO,
                            SNSService snsService) {
        this.topicDAO = topicDAO;
        this.rebbiDAO = rebbiDAO;
        this.shiurSeriesDAO = shiurSeriesDAO;
        this.userDAO = userDAO;
        this.snsService = snsService;
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

        long seriesId = shiurSeriesDAO.createSeries(rebbiId, topicId, requiresPermission, instId, description);

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

        // Send admin notification about new series creation
        try {
            Map<String, Object> seriesDetails = shiurSeriesDAO.getSeriesDetails(seriesId);
            if (seriesDetails != null) {
                String subject = "New Shiur Series Created";
                String message = String.format(
                    "A new shiur series has been created:\n\n" +
                    "Series ID: %d\n" +
                    "Description: %s\n" +
                    "Topic: %s\n" +
                    "Rebbi: %s\n" +
                    "Institution: %s\n" +
                    "Created by: %s (username: %s)\n",
                    seriesId,
                    seriesDetails.get("description"),
                    seriesDetails.get("topicName"),
                    seriesDetails.get("rebbiName"),
                    seriesDetails.get("institutionName"),
                    current.getFirstName() + " " + current.getLastName(),
                    current.getUsername()
                );
                snsService.publishToAdminTopic(message, subject);
            }
        } catch (Exception e) {
            // Log error but don't fail the request
            // Notification failure shouldn't prevent series creation
            System.err.println("Failed to send admin notification: " + e.getMessage());
        }

        resp.put("success", true);
        resp.put("seriesId", seriesId);
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


