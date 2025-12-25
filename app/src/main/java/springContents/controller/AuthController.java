package springContents.controller;

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

@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserDAO userDAO;
    private final InstitutionDAO institutionDAO;

    @Autowired
    public AuthController(UserDAO userDAO, InstitutionDAO institutionDAO) {
        this.userDAO = userDAO;
        this.institutionDAO = institutionDAO;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials, HttpSession session) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        Map<String, Object> response = new HashMap<>();

        if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Username and password are required");
            return ResponseEntity.badRequest().body(response);
        }

        User user = userDAO.authenticateUser(username, password);

        if (user != null) {
            session.setAttribute("user", user);
            session.setAttribute("username", user.getUsername());
            response.put("success", true);
            response.put("username", user.getUsername());
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Invalid username or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @PostMapping("/create-account")
    @Transactional
    public ResponseEntity<Map<String, Object>> createAccount(@RequestBody Map<String, Object> accountData, HttpSession session) {
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

            // Set session
            session.setAttribute("user", user);
            session.setAttribute("username", user.getUsername());

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

    @GetMapping("/institutions")
    public ResponseEntity<List<Institution>> getInstitutions() {
        List<Institution> institutions = institutionDAO.getAllInstitutions();
        return ResponseEntity.ok(institutions);
    }

    @GetMapping("/current-user")
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");

        if (user != null) {
            response.put("loggedIn", true);
            response.put("username", user.getUsername());
        } else {
            response.put("loggedIn", false);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        session.invalidate();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }
}