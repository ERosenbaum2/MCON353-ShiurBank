package springContents.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;
import springContents.model.User;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for User entities.
 * Handles database operations related to user management including authentication,
 * user creation, username and email validation, and user-institution associations.
 */
@Repository
public class UserDAO {
    
    private final DataSource dataSource;
    private final BCryptPasswordEncoder passwordEncoder;
    
    /**
     * Constructs a new UserDAO with the specified data source.
     *
     * @param dataSource the data source for database connections
     */
    @Autowired
    public UserDAO(DataSource dataSource) {
        this.dataSource = dataSource;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }
    
    /**
     * Checks if a username already exists in the database.
     *
     * @param username the username to check
     * @return true if the username exists, false otherwise
     * @throws RuntimeException if a database error occurs
     */
    public boolean usernameExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking username availability", e);
        }
        return false;
    }
    
    /**
     * Checks if an email address already exists in the database.
     *
     * @param email the email address to check
     * @return true if the email exists, false otherwise
     * @throws RuntimeException if a database error occurs
     */
    public boolean emailExists(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking email availability", e);
        }
        return false;
    }
    
    /**
     * Creates a new user in the database with a hashed password.
     *
     * @param username the unique username for the new user
     * @param password the plain text password to be hashed
     * @param title the user's title
     * @param firstName the user's first name
     * @param lastName the user's last name
     * @param email the user's email address
     * @return the created User object with the generated user ID
     * @throws RuntimeException if a database error occurs or user creation fails
     */
    public User createUser(String username, String password, String title, String firstName, 
                          String lastName, String email) {
        String hashedPassword = passwordEncoder.encode(password);
        String sql = "INSERT INTO users (username, hashed_pwd, title, fname, lname, email) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, username);
            stmt.setString(2, hashedPassword);
            stmt.setString(3, title);
            stmt.setString(4, firstName);
            stmt.setString(5, lastName);
            stmt.setString(6, email);
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating user failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    Long userId = generatedKeys.getLong(1);
                    User user = new User();
                    user.setUserId(userId);
                    user.setUsername(username);
                    user.setTitle(title);
                    user.setFirstName(firstName);
                    user.setLastName(lastName);
                    user.setEmail(email);
                    return user;
                } else {
                    throw new SQLException("Creating user failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            DataSourceUtils.releaseConnection(conn, dataSource);
            throw new RuntimeException("Error creating user", e);
        }
        // Note: Don't release connection here if it's part of a transaction
        // Spring will handle it when the transaction completes
    }
    
    /**
     * Authenticates a user by verifying the username and password.
     *
     * @param username the username to authenticate
     * @param password the plain text password to verify
     * @return the User object if authentication succeeds, null otherwise
     * @throws RuntimeException if a database error occurs
     */
    public User authenticateUser(String username, String password) {
        String sql = "SELECT user_id, username, hashed_pwd, title, fname, lname, email " +
                     "FROM users WHERE username = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String hashedPassword = rs.getString("hashed_pwd");
                    if (passwordEncoder.matches(password, hashedPassword)) {
                        User user = new User();
                        user.setUserId(rs.getLong("user_id"));
                        user.setUsername(rs.getString("username"));
                        user.setTitle(rs.getString("title"));
                        user.setFirstName(rs.getString("fname"));
                        user.setLastName(rs.getString("lname"));
                        user.setEmail(rs.getString("email"));
                        return user;
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error authenticating user", e);
        }
        return null;
    }
    
    /**
     * Retrieves a user by their username.
     *
     * @param username the username to look up
     * @return the User object, or null if not found
     * @throws RuntimeException if a database error occurs
     */
    public User getUserByUsername(String username) {
        String sql = "SELECT user_id, username, title, fname, lname, email " +
                     "FROM users WHERE username = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setUserId(rs.getLong("user_id"));
                    user.setUsername(rs.getString("username"));
                    user.setTitle(rs.getString("title"));
                    user.setFirstName(rs.getString("fname"));
                    user.setLastName(rs.getString("lname"));
                    user.setEmail(rs.getString("email"));
                    return user;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error getting user by username", e);
        }
        return null;
    }
    
    /**
     * Associates a user with an institution.
     *
     * @param userId the user ID to associate
     * @param institutionId the institution ID to associate with
     * @throws RuntimeException if a database error occurs or association fails
     */
    public void associateUserWithInstitution(Long userId, Long institutionId) {
        String sql = "INSERT INTO user_institution_assoc (user_id, inst_id) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE user_id = user_id";
        
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, institutionId);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("Failed to associate user with institution");
            }
        } catch (SQLException e) {
            DataSourceUtils.releaseConnection(conn, dataSource);
            throw new RuntimeException("Error associating user with institution: " + e.getMessage(), e);
        }
        // Note: Don't release connection here if it's part of a transaction
    }

    /**
     * Retrieves all users from the database, ordered by last name and first name.
     * Used for admin selection interfaces.
     *
     * @return a list of all users
     * @throws RuntimeException if a database error occurs
     */
    public List<User> getAllUsers() {
        String sql = "SELECT user_id, username, title, fname, lname, email FROM users ORDER BY lname, fname";
        List<User> users = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                User user = new User();
                user.setUserId(rs.getLong("user_id"));
                user.setUsername(rs.getString("username"));
                user.setTitle(rs.getString("title"));
                user.setFirstName(rs.getString("fname"));
                user.setLastName(rs.getString("lname"));
                user.setEmail(rs.getString("email"));
                users.add(user);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching all users", e);
        }
        
        return users;
    }
}

