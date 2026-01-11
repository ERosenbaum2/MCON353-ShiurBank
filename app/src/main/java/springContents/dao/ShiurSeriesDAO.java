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

/**
 * Data Access Object for ShiurSeries entities.
 * Handles database operations related to shiur series including creation, updates,
 * deletion, gabbai and participant management, and series retrieval.
 */
@Repository
public class ShiurSeriesDAO {

    private final DataSource dataSource;

    /**
     * Constructs a new ShiurSeriesDAO with the specified data source.
     *
     * @param dataSource the data source for database connections
     */
    @Autowired
    public ShiurSeriesDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Creates a new shiur series in the database.
     *
     * @param rebbiId the ID of the Rebbi delivering the series
     * @param topicId the ID of the topic for the series
     * @param requiresPermission whether the series requires permission to access
     * @param instId the ID of the institution hosting the series
     * @param description the description of the series
     * @return the generated series ID
     * @throws RuntimeException if a database error occurs or series creation fails
     */
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

    /**
     * Updates the SNS topic ARN for a series.
     *
     * @param seriesId the series ID
     * @param topicArn the SNS topic ARN to set
     * @throws RuntimeException if a database error occurs or update fails
     */
    public void updateSeriesTopicArn(Long seriesId, String topicArn) {
        String sql = "UPDATE shiur_series SET sns_topic_arn = ? WHERE series_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, topicArn);
            stmt.setLong(2, seriesId);

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Updating series topic ARN failed, no rows affected.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error updating series topic ARN", e);
        }
    }

    /**
     * Retrieves the SNS topic ARN for a series.
     *
     * @param seriesId the series ID
     * @return the topic ARN, or null if not set
     * @throws RuntimeException if a database error occurs
     */
    public String getSeriesTopicArn(Long seriesId) {
        String sql = "SELECT sns_topic_arn FROM shiur_series WHERE series_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, seriesId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("sns_topic_arn");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching series topic ARN", e);
        }

        return null;
    }

    /**
     * Deletes a series from the database. This will trigger CASCADE delete on related records.
     *
     * @param seriesId the series ID to delete
     * @throws RuntimeException if a database error occurs
     */
    public void deleteSeries(Long seriesId) {
        String sql = "DELETE FROM shiur_series WHERE series_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, seriesId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting series", e);
        }
    }

    /**
     * Retrieves all series where the specified user is a gabbai.
     *
     * @param userId the user ID of the gabbai
     * @return a list of series maps with details including pending status
     * @throws RuntimeException if a database error occurs
     */
    public List<Map<String, Object>> getSeriesForGabbai(Long userId) {
        String sql =
                "SELECT s.series_id, s.description, s.sns_topic_arn, " +
                        "       t.name AS topic_name, " +
                        "       CONCAT(r.title, ' ', r.fname, ' ', r.lname) AS rebbi_name, " +
                        "       i.name AS inst_name, " +
                        "       CASE WHEN pp.series_id IS NOT NULL THEN 1 ELSE 0 END AS is_pending " +
                        "FROM gabbaim g " +
                        "JOIN shiur_series s ON g.series_id = s.series_id " +
                        "JOIN topics t ON s.topic_id = t.topic_id " +
                        "JOIN rebbeim r ON s.rebbi_id = r.rebbi_id " +
                        "JOIN institutions i ON s.inst_id = i.inst_id " +
                        "LEFT JOIN series_pending_approval pp ON s.series_id = pp.series_id " +
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
                    row.put("snsTopicArn", rs.getString("sns_topic_arn"));
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

    /**
     * Retrieves detailed information about a specific series.
     *
     * @param seriesId the series ID to look up
     * @return a map containing series details, or null if not found
     * @throws RuntimeException if a database error occurs
     */
    public Map<String, Object> getSeriesDetails(Long seriesId) {
        String sql =
                "SELECT s.series_id, s.description, s.sns_topic_arn, " +
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
                    row.put("snsTopicArn", rs.getString("sns_topic_arn"));
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

    /**
     * Checks if a gabbai is already a gabbai for another series from the same Rabbi.
     *
     * @param userId the user ID of the gabbai
     * @param rebbiId the Rabbi ID
     * @return true if the gabbai is already a gabbai for another series from this Rabbi, false otherwise
     * @throws RuntimeException if a database error occurs
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

    /**
     * Checks if a user is a gabbai for a specific series.
     *
     * @param userId the user ID
     * @param seriesId the series ID
     * @return true if the user is a gabbai for this series, false otherwise
     * @throws RuntimeException if a database error occurs
     */
    public boolean isGabbaiForSeries(Long userId, Long seriesId) {
        String sql = "SELECT COUNT(*) FROM gabbaim WHERE user_id = ? AND series_id = ?";

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
            throw new RuntimeException("Error checking if user is gabbai for series", e);
        }

        return false;
    }

    /**
     * Adds a user as a gabbai for a series.
     *
     * @param userId the user ID to add as gabbai
     * @param seriesId the series ID
     * @throws RuntimeException if a database error occurs
     */
    public void addGabbai(Long userId, Long seriesId) {
        String sql = "INSERT INTO gabbaim (user_id, series_id) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE gabbaim.user_id = gabbaim.user_id";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            stmt.setLong(2, seriesId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error adding gabbai", e);
        }
    }

    /**
     * Adds a user as a participant for a series.
     *
     * @param userId the user ID to add as participant
     * @param seriesId the series ID
     * @throws RuntimeException if a database error occurs
     */
    public void addParticipant(Long userId, Long seriesId) {
        String sql = "INSERT INTO shiur_participants (user_id, series_id) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE shiur_participants.user_id = shiur_participants.user_id";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            stmt.setLong(2, seriesId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error adding participant", e);
        }
    }

    /**
     * Retrieves all series where the user is either a gabbai or participant.
     *
     * @param userId the user ID
     * @return a list of series maps with role information
     * @throws RuntimeException if a database error occurs
     */
    public List<Map<String, Object>> getAllSeriesForUser(Long userId) {
        String sql =
                "SELECT s.series_id, s.description, s.sns_topic_arn, " +
                        "       t.name AS topic_name, " +
                        "       CONCAT(r.title, ' ', r.fname, ' ', r.lname) AS rebbi_name, " +
                        "       i.name AS inst_name, " +
                        "       CASE WHEN pp.series_id IS NOT NULL THEN 1 ELSE 0 END AS is_pending, " +
                        "       CASE WHEN g.user_id IS NOT NULL THEN 1 ELSE 0 END AS is_gabbai " +
                        "FROM shiur_series s " +
                        "JOIN topics t ON s.topic_id = t.topic_id " +
                        "JOIN rebbeim r ON s.rebbi_id = r.rebbi_id " +
                        "JOIN institutions i ON s.inst_id = i.inst_id " +
                        "LEFT JOIN gabbaim g ON s.series_id = g.series_id AND g.user_id = ? " +
                        "LEFT JOIN shiur_participants sp ON s.series_id = sp.series_id AND sp.user_id = ? " +
                        "LEFT JOIN series_pending_approval pp ON s.series_id = pp.series_id " +
                        "WHERE g.user_id IS NOT NULL OR sp.user_id IS NOT NULL " +
                        "ORDER BY s.series_id DESC";

        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            stmt.setLong(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    long seriesId = rs.getLong("series_id");
                    String topicName = rs.getString("topic_name");
                    String rebbiName = rs.getString("rebbi_name");
                    boolean isPending = rs.getInt("is_pending") == 1;
                    boolean isGabbai = rs.getInt("is_gabbai") == 1;

                    row.put("seriesId", seriesId);
                    row.put("description", rs.getString("description"));
                    row.put("topicName", topicName);
                    row.put("rebbiName", rebbiName);
                    row.put("institutionName", rs.getString("inst_name"));
                    row.put("snsTopicArn", rs.getString("sns_topic_arn"));
                    row.put("displayName", topicName + " — " + rebbiName);
                    row.put("isPending", isPending);
                    row.put("isGabbai", isGabbai);

                    result.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching series for user", e);
        }

        return result;
    }
}