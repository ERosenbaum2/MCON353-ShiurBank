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
 * Data Access Object for participant approval operations.
 * Handles database operations related to pending participant applications,
 * participant status checking, and gabbai information retrieval.
 */
@Repository
public class ParticipantApprovalDAO {

    private final DataSource dataSource;

    /**
     * Constructs a new ParticipantApprovalDAO with the specified data source.
     *
     * @param dataSource the data source for database connections
     */
    @Autowired
    public ParticipantApprovalDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Checks if a user already has a pending application for a series.
     *
     * @param userId the user ID to check
     * @param seriesId the series ID to check
     * @return true if there is a pending application, false otherwise
     * @throws RuntimeException if a database error occurs
     */
    public boolean hasPendingApplication(Long userId, Long seriesId) {
        String sql = "SELECT COUNT(*) FROM users_pending_approval_to_series WHERE user_id = ? AND series_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, seriesId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking pending application", e);
        }
        return false;
    }

    /**
     * Checks if a user is already a participant in a series.
     *
     * @param userId the user ID to check
     * @param seriesId the series ID to check
     * @return true if the user is a participant, false otherwise
     * @throws RuntimeException if a database error occurs
     */
    public boolean isParticipant(Long userId, Long seriesId) {
        String sql = "SELECT COUNT(*) FROM shiur_participants WHERE user_id = ? AND series_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, seriesId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking participant status", e);
        }
        return false;
    }

    /**
     * Adds a user to the pending approval table for a series.
     *
     * @param userId the user ID to add
     * @param seriesId the series ID
     * @throws RuntimeException if a database error occurs
     */
    public void addPendingApproval(Long userId, Long seriesId) {
        String sql = "INSERT INTO users_pending_approval_to_series (user_id, series_id) VALUES (?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, seriesId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error adding pending approval", e);
        }
    }

    /**
     * Removes a pending approval entry for a user and series.
     *
     * @param userId the user ID
     * @param seriesId the series ID
     * @throws RuntimeException if a database error occurs
     */
    public void removePendingApproval(Long userId, Long seriesId) {
        String sql = "DELETE FROM users_pending_approval_to_series WHERE user_id = ? AND series_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, seriesId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error removing pending approval", e);
        }
    }

    /**
     * Retrieves all pending participants for a series.
     *
     * @param seriesId the series ID
     * @return a list of pending participant information maps
     * @throws RuntimeException if a database error occurs
     */
    public List<Map<String, Object>> getPendingParticipants(Long seriesId) {
        String sql = "SELECT p.pending_id, p.user_id, u.username, u.title, u.fname, u.lname, u.email " +
                "FROM users_pending_approval_to_series p " +
                "JOIN users u ON p.user_id = u.user_id " +
                "WHERE p.series_id = ? " +
                "ORDER BY p.pending_id";

        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, seriesId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("pendingId", rs.getLong("pending_id"));
                    row.put("userId", rs.getLong("user_id"));
                    row.put("username", rs.getString("username"));
                    row.put("fullName", rs.getString("title") + " " +
                            rs.getString("fname") + " " + rs.getString("lname"));
                    row.put("email", rs.getString("email"));
                    result.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching pending participants", e);
        }

        return result;
    }

    /**
     * Retrieves gabbai information for a series including name and email.
     *
     * @param seriesId the series ID
     * @return a list of gabbai information maps
     * @throws RuntimeException if a database error occurs
     */
    public List<Map<String, Object>> getGabbaiInfo(Long seriesId) {
        String sql = "SELECT u.user_id, u.username, u.title, u.fname, u.lname, u.email " +
                "FROM gabbaim g " +
                "JOIN users u ON g.user_id = u.user_id " +
                "WHERE g.series_id = ? " +
                "ORDER BY g.gabbai_id";

        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, seriesId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("userId", rs.getLong("user_id"));
                    row.put("username", rs.getString("username"));
                    row.put("fullName", rs.getString("title") + " " +
                            rs.getString("fname") + " " + rs.getString("lname"));
                    row.put("email", rs.getString("email"));
                    result.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching gabbai info", e);
        }

        return result;
    }

    /**
     * Retrieves series details including the requires_permission flag.
     *
     * @param seriesId the series ID
     * @return a map containing series application information, or null if not found
     * @throws RuntimeException if a database error occurs
     */
    public Map<String, Object> getSeriesApplicationInfo(Long seriesId) {
        String sql = "SELECT s.series_id, s.requires_permission, s.description, " +
                "t.name AS topic_name, " +
                "CONCAT(r.title, ' ', r.fname, ' ', r.lname) AS rebbi_name, " +
                "i.name AS institution_name " +
                "FROM shiur_series s " +
                "JOIN topics t ON s.topic_id = t.topic_id " +
                "JOIN rebbeim r ON s.rebbi_id = r.rebbi_id " +
                "JOIN institutions i ON s.inst_id = i.inst_id " +
                "WHERE s.series_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, seriesId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("seriesId", rs.getLong("series_id"));
                    row.put("requiresPermission", rs.getBoolean("requires_permission"));
                    row.put("description", rs.getString("description"));
                    row.put("topicName", rs.getString("topic_name"));
                    row.put("rebbiName", rs.getString("rebbi_name"));
                    row.put("institutionName", rs.getString("institution_name"));
                    return row;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching series application info", e);
        }

        return null;
    }
}