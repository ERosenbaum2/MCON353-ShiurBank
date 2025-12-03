package springContents.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;
import springContents.model.User;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class UserDAO {
    
    private final DataSource dataSource;
    private final BCryptPasswordEncoder passwordEncoder;
    
    @Autowired
    public UserDAO(DataSource dataSource) {
        this.dataSource = dataSource;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }
    
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
    
    public User createUser(String username, String password, String title, String firstName, 
                          String lastName, String email) {
        String hashedPassword = passwordEncoder.encode(password);
        String sql = "INSERT INTO users (username, hashed_pwd, title, fname, lname, email) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
            throw new RuntimeException("Error creating user", e);
        }
    }
    
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
    
    public void associateUserWithInstitution(Long userId, Long institutionId) {
        String sql = "INSERT INTO user_institution_assoc (user_id, inst_id) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE user_id = user_id";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, institutionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error associating user with institution", e);
        }
    }
}

