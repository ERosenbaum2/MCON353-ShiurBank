package springContents.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import springContents.dao.InstitutionDAO;
import springContents.dao.UserDAO;
import springContents.model.Institution;
import springContents.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller for authentication and user account management.
 * Handles user login, account creation, logout, and session management.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserDAO userDAO;
    private final InstitutionDAO institutionDAO;

    /**
     * Constructs a new AuthController with the specified DAOs.
     *
     * @param userDAO the UserDAO for user operations
     * @param institutionDAO the InstitutionDAO for institution operations
     */
    @Autowired
    public AuthController(UserDAO userDAO, InstitutionDAO institutionDAO) {
        this.userDAO = userDAO;
        this.institutionDAO = institutionDAO;
    }

    /**
     * Authenticates a user and creates a session.
     *
     * @param credentials a map containing "username" and "password"
     * @param request the HTTP Servlet Request
     * @param httpResponse the HTTP Servlet Response to store user information
     * @return a response map with success status and username, or error message
     * @throws RuntimeException if a database connection error occurs
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials, 
                                                       HttpServletRequest request, 
                                                       HttpServletResponse httpResponse) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        Map<String, Object> response = new HashMap<>();

        if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Username and password are required");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            User user = userDAO.authenticateUser(username, password);

            if (user != null) {
                // Get or create session - this ensures the session cookie is created
                HttpSession session = request.getSession(true);
                
                // Log session info before setting attributes
                logger.info("Login: Session ID before setting attributes: {}", session.getId());
                logger.info("Login: Session is new: {}", session.isNew());
                
                session.setAttribute("user", user);
                session.setAttribute("username", user.getUsername());
                
                // Touch the session to ensure it's persisted and cookie is sent
                session.setAttribute("lastAccess", System.currentTimeMillis());
                
                // Log session info after setting attributes
                logger.info("Login: Session ID after setting attributes: {}", session.getId());
                logger.info("Login: User {} logged in successfully", username);
                
                // Ensure session cookie is set by accessing session ID
                String sessionId = session.getId();
                logger.info("Login: Session cookie should be set with ID: {}", sessionId);
                
                // Manually set the JSESSIONID cookie
                // Spring Boot 3.x with ResponseEntity doesn't automatically send session cookies
                // Try both methods: HttpServletResponse and ResponseEntity header
                Cookie sessionCookie = new Cookie("JSESSIONID", sessionId);
                sessionCookie.setPath("/");
                sessionCookie.setHttpOnly(true);
                sessionCookie.setMaxAge(-1); // Session cookie (expires when browser closes)
                httpResponse.addCookie(sessionCookie);
                
                // Also try setting it as a header (some browsers/clients need this)
                String cookieHeader = String.format("JSESSIONID=%s; Path=/; HttpOnly; SameSite=Lax", sessionId);
                logger.info("Login: Setting cookie via HttpServletResponse and header: {}", cookieHeader);
                
                response.put("success", true);
                response.put("username", user.getUsername());
                response.put("sessionId", sessionId); // Include session ID for debugging
                
                // Use ResponseEntity with explicit Set-Cookie header
                return ResponseEntity.ok()
                    .header("Set-Cookie", cookieHeader)
                    .body(response);
            } else {
                response.put("success", false);
                response.put("message", "Invalid username or password");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
        } catch (RuntimeException e) {
            // Check if it's a database connection error
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("Connection") || 
                                     errorMsg.contains("Communications link failure") ||
                                     errorMsg.contains("Unknown database") ||
                                     errorMsg.contains("Access denied"))) {
                response.put("success", false);
                response.put("message", "Database connection error. The database might be down. Please start the database first.");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            // Re-throw if it's not a connection error
            throw e;
        }
    }

    /**
     * Creates a new user account with optional institution associations.
     *
     * @param accountData a map containing user account information including username, password,
     *                    title, firstName, lastName, email, and optionally institutions
     * @param request the HTTP Servlet Request
     * @param httpResponse the HTTP Servlet Response to store user information upon successful creation
     * @return a response map with success status and username, or validation errors
     * @throws RuntimeException if account creation fails
     */
    @PostMapping("/create-account")
    @Transactional
    public ResponseEntity<Map<String, Object>> createAccount(@RequestBody Map<String, Object> accountData, 
                                                              HttpServletRequest request,
                                                              HttpServletResponse httpResponse) {
        Map<String, Object> response = new HashMap<>();
        Map<String, String> errors = new HashMap<>();

        // Extract and validate required fields
        String username = (String) accountData.get("username");
        String password = (String) accountData.get("password");
        String title = (String) accountData.get("title");
        String firstName = (String) accountData.get("firstName");
        String lastName = (String) accountData.get("lastName");
        String email = (String) accountData.get("email");

        // Handle institution IDs - JSON deserializes numbers as Integer, need to convert to Long
        List<Long> institutionIds = null;
        Object institutionsObj = accountData.get("institutions");
        if (institutionsObj != null) {
            @SuppressWarnings("unchecked")
            List<Object> institutionsList = (List<Object>) institutionsObj;
            institutionIds = new ArrayList<>();
            for (Object instId : institutionsList) {
                if (instId instanceof Integer) {
                    institutionIds.add(((Integer) instId).longValue());
                } else if (instId instanceof Long) {
                    institutionIds.add((Long) instId);
                } else if (instId instanceof Number) {
                    institutionIds.add(((Number) instId).longValue());
                }
            }
        }

        logger.info("Creating account for username: {}, institutions: {}", username, institutionIds);

        // Validate required fields
        if (username == null || username.trim().isEmpty()) {
            errors.put("username", "Username is required");
        } else if (userDAO.usernameExists(username)) {
            errors.put("username", "Username already taken");
        }

        if (password == null || password.trim().isEmpty()) {
            errors.put("password", "Password is required");
        }

        if (title == null || title.trim().isEmpty()) {
            errors.put("title", "Title is required");
        }

        if (firstName == null || firstName.trim().isEmpty()) {
            errors.put("firstName", "First name is required");
        }

        if (lastName == null || lastName.trim().isEmpty()) {
            errors.put("lastName", "Last name is required");
        }

        if (email == null || email.trim().isEmpty()) {
            errors.put("email", "Email is required");
        } else if (!isValidEmail(email)) {
            errors.put("email", "Please enter a valid email address");
        } else if (userDAO.emailExists(email)) {
            errors.put("email", "Email already taken");
        }

        if (!errors.isEmpty()) {
            response.put("success", false);
            response.put("errors", errors);
            return ResponseEntity.badRequest().body(response);
        }

        // Create user
        try {
            User user = userDAO.createUser(username, password, title, firstName, lastName, email);
            logger.info("User created with ID: {}", user.getUserId());

            // Associate with institutions if provided
            if (institutionIds != null && !institutionIds.isEmpty()) {
                logger.info("Associating user {} with {} institutions", user.getUserId(), institutionIds.size());
                for (Long instId : institutionIds) {
                    logger.info("Associating user {} with institution {}", user.getUserId(), instId);
                    userDAO.associateUserWithInstitution(user.getUserId(), instId);
                }
                logger.info("Successfully associated user with all institutions");
            } else {
                logger.info("No institutions to associate");
            }

            // Get or create session
            HttpSession session = request.getSession(true);
            
            // Set session
            session.setAttribute("user", user);
            session.setAttribute("username", user.getUsername());
            
            // Manually set the JSESSIONID cookie using HttpServletResponse
            String sessionId = session.getId();
            Cookie sessionCookie = new Cookie("JSESSIONID", sessionId);
            sessionCookie.setPath("/");
            sessionCookie.setHttpOnly(true);
            sessionCookie.setMaxAge(-1); // Session cookie (expires when browser closes)
            httpResponse.addCookie(sessionCookie);
            logger.info("Create-account: Added JSESSIONID cookie to response");

            response.put("success", true);
            response.put("username", user.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating account", e);
            // With @Transactional, if an exception is thrown here, the entire transaction
            // (including user creation) will be rolled back automatically
            response.put("success", false);
            response.put("message", "Error creating account: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Retrieves all institutions from the database.
     *
     * @return a list of all institutions
     */
    @GetMapping("/institutions")
    public ResponseEntity<List<Institution>> getInstitutions() {
        List<Institution> institutions = institutionDAO.getAllInstitutions();
        return ResponseEntity.ok(institutions);
    }

    /**
     * Retrieves the current logged-in user information from the session.
     *
     * @param request the HTTP Servlet Request
     * @return a response map with loggedIn status and username if authenticated
     */
    @GetMapping("/current-user")
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        // Get session - don't create if it doesn't exist
        HttpSession session = request.getSession(false);
        
        if (session == null) {
            logger.info("Current-user: No session exists");
            response.put("loggedIn", false);
            response.put("sessionId", null);
            return ResponseEntity.ok(response);
        }
        
        // Log session info for debugging
        logger.info("Current-user: Session ID: {}", session.getId());
        logger.info("Current-user: Session is new: {}", session.isNew());
        
        User user = (User) session.getAttribute("user");

        if (user != null) {
            logger.info("Current-user: User {} is logged in", user.getUsername());
            response.put("loggedIn", true);
            response.put("username", user.getUsername());
            response.put("sessionId", session.getId()); // Include session ID for debugging
        } else {
            logger.info("Current-user: No user found in session");
            response.put("loggedIn", false);
            response.put("sessionId", session.getId()); // Include session ID for debugging
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Logs out the current user by invalidating the session.
     *
     * @param session the HTTP session to invalidate
     * @return a response map with success status
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        session.invalidate();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    /**
     * Validates an email address format.
     *
     * @param email the email address to validate
     * @return true if the email contains both "@" and ".", false otherwise
     */
    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }
}