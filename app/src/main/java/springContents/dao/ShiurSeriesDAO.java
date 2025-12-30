package springContents.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ShiurSeriesDAO {

    private final DataSource dataSource;

    @Autowired
    public ShiurSeriesDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long createSeries(Long rebbiId,
                             Long topicId,
                             boolean requiresPermission,
                             Long instId,
                             String description) {
        String sql = "INSERT INTO shiur_series (rebbi_id, topic_id, requires_permission, inst_id, description) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, rebbiId);
            stmt.setLong(2, topicId);
            stmt.setBoolean(3, requiresPermission);
            stmt.setLong(4, instId);
            stmt.setString(5, description);

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Creating series failed, no rows affected.");
            }

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                } else {
                    throw new SQLException("Creating series failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creating series", e);
        }
    }

    public void addGabbai(Long userId, Long seriesId) {
        String sql = "INSERT INTO gabbaim (user_id, series_id) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE user_id = user_id";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            stmt.setLong(2, seriesId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error adding gabbai", e);
        }
    }

    public List<Map<String, Object>> getSeriesForGabbai(Long userId) {
        String sql =
                "SELECT s.series_id, s.description, " +
                        "       t.name AS topic_name, " +
                        "       CONCAT(r.title, ' ', r.fname, ' ', r.lname) AS rebbi_name, " +
                        "       i.name AS inst_name, " +
                        "       CASE WHEN pp.series_id IS NOT NULL THEN 1 ELSE 0 END AS is_pending " +
                        "FROM gabbaim g " +
                        "JOIN shiur_series s ON g.series_id = s.series_id " +
                        "JOIN topics t ON s.topic_id = t.topic_id " +
                        "JOIN rebbeim r ON s.rebbi_id = r.rebbi_id " +
                        "JOIN institutions i ON s.inst_id = i.inst_id " +
                        "LEFT JOIN pending_permission pp ON s.series_id = pp.series_id " +
                        "WHERE g.user_id = ? " +
                        "ORDER BY s.series_id DESC";

        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    long seriesId = rs.getLong("series_id");
                    String topicName = rs.getString("topic_name");
                    String rebbiName = rs.getString("rebbi_name");
                    boolean isPending = rs.getInt("is_pending") == 1;

                    row.put("seriesId", seriesId);
                    row.put("description", rs.getString("description"));
                    row.put("topicName", topicName);
                    row.put("rebbiName", rebbiName);
                    row.put("institutionName", rs.getString("inst_name"));
                    row.put("displayName", topicName + " — " + rebbiName);
                    row.put("isPending", isPending);

                    result.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching series for gabbai", e);
        }

        return result;
    }

    public Map<String, Object> getSeriesDetails(Long seriesId) {
        String sql =
                "SELECT s.series_id, s.description, " +
                        "       t.name AS topic_name, " +
                        "       CONCAT(r.title, ' ', r.fname, ' ', r.lname) AS rebbi_name, " +
                        "       i.name AS inst_name " +
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
                    row.put("description", rs.getString("description"));
                    row.put("topicName", rs.getString("topic_name"));
                    row.put("rebbiName", rs.getString("rebbi_name"));
                    row.put("institutionName", rs.getString("inst_name"));
                    row.put("displayName",
                            rs.getString("topic_name") + " — " + rs.getString("rebbi_name"));
                    return row;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching series details", e);
        }

        return null;
    }

    public List<String> getSubscriberEmailsForSeries(Long seriesId) {
        String sql =
            "SELECT DISTINCT u.email " +
            "FROM subscribers s " +
            "JOIN users u ON s.user_id = u.user_id " +
            "WHERE s.series_id = ?";

        List<String> emails = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, seriesId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String email = rs.getString("email");
                    if (email != null && !email.trim().isEmpty()) {
                        emails.add(email.trim());
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching subscriber emails for series", e);
        }

        return emails;
    }

    public List<Map<String, Object>> searchSeries(String query, Long userId) {
        String searchPattern = "%" + query.toLowerCase() + "%";
        String sql =
                "SELECT DISTINCT s.series_id, s.description, s.requires_permission, " +
                        "       t.name AS topic_name, " +
                        "       CONCAT(r.title, ' ', r.fname, ' ', r.lname) AS rebbi_name, " +
                        "       i.name AS inst_name, " +
                        "       CASE WHEN (sp.user_id IS NOT NULL OR g.user_id IS NOT NULL) THEN 1 ELSE 0 END AS has_access " +
                        "FROM shiur_series s " +
                        "JOIN topics t ON s.topic_id = t.topic_id " +
                        "JOIN rebbeim r ON s.rebbi_id = r.rebbi_id " +
                        "JOIN institutions i ON s.inst_id = i.inst_id " +
                        "LEFT JOIN shiur_participants sp ON s.series_id = sp.series_id AND sp.user_id = ? " +
                        "LEFT JOIN gabbaim g ON s.series_id = g.series_id AND g.user_id = ? " +
                        "WHERE LOWER(s.description) LIKE ? " +
                        "   OR LOWER(t.name) LIKE ? " +
                        "   OR LOWER(CONCAT(r.title, ' ', r.fname, ' ', r.lname)) LIKE ? " +
                        "   OR LOWER(i.name) LIKE ? " +
                        "ORDER BY has_access DESC, s.series_id DESC";

        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            stmt.setLong(2, userId);
            stmt.setString(3, searchPattern);
            stmt.setString(4, searchPattern);
            stmt.setString(5, searchPattern);
            stmt.setString(6, searchPattern);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    long seriesId = rs.getLong("series_id");
                    String topicName = rs.getString("topic_name");
                    String rebbiName = rs.getString("rebbi_name");
                    boolean hasAccess = rs.getInt("has_access") == 1;
                    boolean requiresPermission = rs.getBoolean("requires_permission");

                    row.put("seriesId", seriesId);
                    row.put("description", rs.getString("description"));
                    row.put("topicName", topicName);
                    row.put("rebbiName", rebbiName);
                    row.put("institutionName", rs.getString("inst_name"));
                    row.put("displayName", topicName + " — " + rebbiName);
                    row.put("hasAccess", hasAccess);
                    row.put("requiresPermission", requiresPermission);

                    result.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error searching series", e);
        }

        return result;
    }
}

    /**
     * Check if a gabbai is already a gabbai for another series from the same Rabbi
     * @param userId The user ID of the gabbai
     * @param rebbiId The Rabbi ID
     * @return true if the gabbai is already a gabbai for another series from this Rabbi, false otherwise
     */
    public boolean isGabbaiForSameRebbi(Long userId, Long rebbiId) {
        String sql =
                "SELECT COUNT(*) " +
                        "FROM gabbaim g " +
                        "JOIN shiur_series s ON g.series_id = s.series_id " +
                        "WHERE g.user_id = ? AND s.rebbi_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            stmt.setLong(2, rebbiId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking if gabbai is for same Rabbi", e);
        }

        return false;
    }
}