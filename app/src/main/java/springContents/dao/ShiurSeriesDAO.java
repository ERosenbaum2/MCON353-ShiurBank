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
            "       i.name AS inst_name " +
            "FROM gabbaim g " +
            "JOIN shiur_series s ON g.series_id = s.series_id " +
            "JOIN topics t ON s.topic_id = t.topic_id " +
            "JOIN rebbeim r ON s.rebbi_id = r.rebbi_id " +
            "JOIN institutions i ON s.inst_id = i.inst_id " +
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

                    row.put("seriesId", seriesId);
                    row.put("description", rs.getString("description"));
                    row.put("topicName", topicName);
                    row.put("rebbiName", rebbiName);
                    row.put("institutionName", rs.getString("inst_name"));
                    row.put("displayName", topicName + " – " + rebbiName);

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
                            rs.getString("topic_name") + " – " + rs.getString("rebbi_name"));
                    return row;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching series details", e);
        }

        return null;
    }
}


