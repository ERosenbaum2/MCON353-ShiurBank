package springContents.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for admin operations.
 * Handles database operations related to administrator management, including
 * admin status checking, pending permission management, and admin email retrieval.
 */
@Repository
public class AdminDAO {

    private final DataSource dataSource;

    /**
     * Constructs a new AdminDAO with the specified data source.
     *
     * @param dataSource the data source for database connections
     */
    @Autowired
    public AdminDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Checks if a user is an admin.
     *
     * @param userId the user ID to check
     * @return true if the user is an admin, false otherwise
     * @throws RuntimeException if a database error occurs
     */
    public boolean isAdmin(Long userId) {
        String sql = "SELECT COUNT(*) FROM admins WHERE user_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking admin status", e);
        }
        return false;
    }

    /**
     * Retrieves all pending permissions with series details.
     *
     * @return a list of pending permission maps with series information
     * @throws RuntimeException if a database error occurs
     */
    public List<Map<String, Object>> getPendingPermissions() {
        String sql =
            "SELECT pp.pending_id, pp.series_id, " +
            "       s.description, " +
            "       t.name AS topic_name, " +
            "       CONCAT(r.title, ' ', r.fname, ' ', r.lname) AS rebbi_name, " +
            "       i.name AS inst_name " +
            "FROM series_pending_approval pp " +
            "JOIN shiur_series s ON pp.series_id = s.series_id " +
            "JOIN topics t ON s.topic_id = t.topic_id " +
            "JOIN rebbeim r ON s.rebbi_id = r.rebbi_id " +
            "JOIN institutions i ON s.inst_id = i.inst_id " +
            "ORDER BY pp.pending_id DESC";

        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("pendingId", rs.getLong("pending_id"));
                    row.put("seriesId", rs.getLong("series_id"));
                    row.put("description", rs.getString("description"));
                    row.put("topicName", rs.getString("topic_name"));
                    row.put("rebbiName", rs.getString("rebbi_name"));
                    row.put("institutionName", rs.getString("inst_name"));
                    row.put("displayName", 
                            rs.getString("topic_name") + " – " + rs.getString("rebbi_name"));
                    result.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching pending permissions", e);
        }

        return result;
    }

    /**
     * Removes a pending permission by pending_id, effectively verifying it.
     *
     * @param pendingId the pending permission ID to verify
     * @throws RuntimeException if a database error occurs or pending permission not found
     */
    public void verifyPendingPermission(Long pendingId) {
        String sql = "DELETE FROM series_pending_approval WHERE pending_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, pendingId);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new RuntimeException("Pending permission not found or already verified");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error verifying pending permission", e);
        }
    }

    /**
     * Adds a user as an admin.
     *
     * @param userId the user ID to add as admin
     * @throws RuntimeException if a database error occurs
     */
    public void addAdmin(Long userId) {
        String sql = "INSERT INTO admins (user_id) VALUES (?) " +
                     "ON DUPLICATE KEY UPDATE user_id = user_id";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error adding admin: " + e.getMessage(), e);
        }
    }

    /**
     * Adds a pending permission entry for a series.
     *
     * @param seriesId the series ID
     * @throws RuntimeException if a database error occurs
     */
    public void addPendingPermission(Long seriesId) {
        String sql = "INSERT INTO series_pending_approval (series_id) VALUES (?) " +
                     "ON DUPLICATE KEY UPDATE series_id = series_id";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, seriesId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error adding pending permission", e);
        }
    }

    /**
     * Retrieves all admin email addresses.
     *
     * @return a list of admin email addresses
     * @throws RuntimeException if a database error occurs
     */
    public List<String> getAdminEmails() {
        String sql =
            "SELECT u.email " +
            "FROM admins a " +
            "JOIN users u ON a.user_id = u.user_id";

        List<String> emails = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String email = rs.getString("email");
                    if (email != null && !email.trim().isEmpty()) {
                        emails.add(email);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching admin emails", e);
        }

        return emails;
    }
}