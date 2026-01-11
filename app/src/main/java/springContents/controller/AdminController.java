package springContents.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import springContents.dao.AdminDAO;
import springContents.dao.UserDAO;
import springContents.model.User;
import springContents.service.RdsService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminDAO adminDAO;
    private final RdsService rdsService;
    private final UserDAO userDAO;

    @Autowired
    public AdminController(AdminDAO adminDAO, RdsService rdsService, UserDAO userDAO) {
        this.adminDAO = adminDAO;
        this.rdsService = rdsService;
        this.userDAO = userDAO;
    }

    /**
     * Check if the current user is an admin
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkAdminStatus(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");
        
        if (user == null) {
            response.put("isAdmin", false);
            response.put("message", "Not logged in");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        boolean isAdmin = adminDAO.isAdmin(user.getUserId());
        response.put("isAdmin", isAdmin);
        return ResponseEntity.ok(response);
    }

    /**
     * Get RDS database status
     * Allows checking status even without login (needed when DB is down)
     */
    @GetMapping("/rds/status")
    public ResponseEntity<Map<String, Object>> getDatabaseStatus(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        
        // Allow status check even without login - needed when DB is down
        // Try admin check if logged in, but don't block
        try {
            if (session.getAttribute("user") != null && !isAdmin(session)) {
                // If we can check and user is not admin, still allow status check
            }
        } catch (Exception e) {
            // DB might be down, allow status check anyway
        }

        try {
            String status = rdsService.getDatabaseStatus();
            response.put("success", true);
            response.put("status", status);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error getting database status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Start the RDS database (requires admin login or session)
     */
    @PostMapping("/rds/start")
    public ResponseEntity<Map<String, Object>> startDatabase(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        
        // Try to check admin status if logged in
        try {
            if (session.getAttribute("user") != null && !isAdmin(session)) {
                response.put("success", false);
                response.put("message", "Admin access required");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        } catch (Exception e) {
            // If admin check fails (likely DB is down), require password via public endpoint
            response.put("success", false);
            response.put("message", "Database is down. Use the public endpoint with admin password.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        try {
            rdsService.startDatabase();
            response.put("success", true);
            response.put("message", "Database start initiated");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error starting database: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Verify admin password (for modal access)
     */
    @PostMapping("/rds/verify-password")
    public ResponseEntity<Map<String, Object>> verifyPassword(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        
        String adminPassword = getAdminPassword();
        String providedPassword = body.get("adminPassword");
        
        if (providedPassword == null || !providedPassword.equals(adminPassword)) {
            response.put("success", false);
            response.put("message", "Invalid admin password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        response.put("success", true);
        response.put("message", "Password verified");
        return ResponseEntity.ok(response);
    }

    /**
     * Public endpoint to start database with admin password
     * Used when database is down and user can't log in
     */
    @PostMapping("/rds/start-public")
    public ResponseEntity<Map<String, Object>> startDatabasePublic(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        
        String adminPassword = getAdminPassword();
        String providedPassword = body.get("adminPassword");
        
        if (providedPassword == null || !providedPassword.equals(adminPassword)) {
            response.put("success", false);
            response.put("message", "Invalid admin password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            rdsService.startDatabase();
            response.put("success", true);
            response.put("message", "Database start initiated");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error starting database: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Public endpoint to stop database with admin password
     */
    @PostMapping("/rds/stop-public")
    public ResponseEntity<Map<String, Object>> stopDatabasePublic(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        
        String adminPassword = getAdminPassword();
        String providedPassword = body.get("adminPassword");
        
        if (providedPassword == null || !providedPassword.equals(adminPassword)) {
            response.put("success", false);
            response.put("message", "Invalid admin password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            rdsService.stopDatabase();
            response.put("success", true);
            response.put("message", "Database stop initiated");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error stopping database: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to get admin password from config
     */
    private String getAdminPassword() {
        String adminPassword = System.getenv("SHIURBANK_ADMIN_PASSWORD");
        if (adminPassword == null || adminPassword.isEmpty()) {
            // Try to read from properties file
            try {
                java.util.Properties props = new java.util.Properties();
                java.io.FileInputStream fis = new java.io.FileInputStream("dbcredentials.properties");
                props.load(fis);
                fis.close();
                adminPassword = props.getProperty("admin.password", "ShiurBank2024!");
            } catch (Exception e) {
                // Fallback to default if file can't be read
                adminPassword = "ShiurBank2024!";
            }
        }
        return adminPassword;
    }

    /**
     * Stop the RDS database
     */
    @PostMapping("/rds/stop")
    public ResponseEntity<Map<String, Object>> stopDatabase(HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, Object> response = new HashMap<>();
        try {
            rdsService.stopDatabase();
            response.put("success", true);
            response.put("message", "Database stop initiated");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error stopping database: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Get all pending permissions
     */
    @GetMapping("/pending-permissions")
    public ResponseEntity<List<Map<String, Object>>> getPendingPermissions(HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Map<String, Object>> pendingPermissions = adminDAO.getPendingPermissions();
        return ResponseEntity.ok(pendingPermissions);
    }

    /**
     * Verify a pending permission (remove it from the table)
     */
    @PostMapping("/pending-permissions/{pendingId}/verify")
    public ResponseEntity<Map<String, Object>> verifyPendingPermission(
            @PathVariable Long pendingId,
            HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, Object> response = new HashMap<>();
        try {
            adminDAO.verifyPendingPermission(pendingId);
            response.put("success", true);
            response.put("message", "Pending permission verified and removed");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error verifying pending permission: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Get all users for admin designation
     */
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers(HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<User> users = userDAO.getAllUsers();
        List<Map<String, Object>> userList = users.stream()
                .map(user -> {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("userId", user.getUserId());
                    userMap.put("username", user.getUsername());
                    userMap.put("title", user.getTitle());
                    userMap.put("firstName", user.getFirstName());
                    userMap.put("lastName", user.getLastName());
                    userMap.put("email", user.getEmail());
                    userMap.put("displayName", 
                            (user.getTitle() != null ? user.getTitle() + " " : "") +
                            user.getFirstName() + " " + user.getLastName() + 
                            " (" + user.getUsername() + ")");
                    userMap.put("isAdmin", adminDAO.isAdmin(user.getUserId()));
                    return userMap;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(userList);
    }

    /**
     * Add a user as an admin
     */
    @PostMapping("/add-admin")
    public ResponseEntity<Map<String, Object>> addAdmin(@RequestBody Map<String, Object> body,
                                                         HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, Object> response = new HashMap<>();
        try {
            Long userId = null;
            Object userIdObj = body.get("userId");
            if (userIdObj instanceof Number) {
                userId = ((Number) userIdObj).longValue();
            } else if (userIdObj != null) {
                userId = Long.parseLong(userIdObj.toString());
            }

            if (userId == null) {
                response.put("success", false);
                response.put("message", "User ID is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Check if user already is an admin
            if (adminDAO.isAdmin(userId)) {
                response.put("success", false);
                response.put("message", "User is already an admin");
                return ResponseEntity.badRequest().body(response);
            }

            adminDAO.addAdmin(userId);
            response.put("success", true);
            response.put("message", "User successfully added as admin");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error adding admin: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to check if the current user is an admin
     */
    private boolean isAdmin(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return false;
        }
        return adminDAO.isAdmin(user.getUserId());
    }
}