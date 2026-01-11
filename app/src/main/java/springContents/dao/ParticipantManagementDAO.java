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
 * Data Access Object for participant management operations.
 * Handles database operations related to managing participants in series,
 * including retrieval, removal, and status checking.
 */
@Repository
public class ParticipantManagementDAO {

    private final DataSource dataSource;

    /**
     * Constructs a new ParticipantManagementDAO with the specified data source.
     *
     * @param dataSource the data source for database connections
     */
    @Autowired
    public ParticipantManagementDAO(DataSource dataSource){
        this.dataSource = dataSource;
    }

    /**
     * Retrieves all participants for a series, sorted by last name alphabetically.
     *
     * @param seriesId the series ID
     * @return a list of participants with their details
     * @throws RuntimeException if a database error occurs
     */
    public List<Map<String, Object>> getParticipants(Long seriesId) {
        String sql =
                "SELECT sp.user_id, " +
                        "       CONCAT(u.title, ' ', u.fname, ' ', u.lname) AS full_name, " +
                        "       u.fname, " +
                        "       u.lname, " +
                        "       u.email, " +
                        "       CASE WHEN g.user_id IS NOT NULL THEN 1 ELSE 0 END AS is_gabbai " +
                        "FROM shiur_participants sp " +
                        "JOIN users u ON sp.user_id = u.user_id " +
                        "LEFT JOIN gabbaim g ON sp.user_id = g.user_id AND sp.series_id = g.series_id " +
                        "WHERE sp.series_id = ? " +
                        "ORDER BY u.lname ASC, u.fname ASC";

        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, seriesId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("userId", rs.getLong("user_id"));
                    row.put("fullName", rs.getString("full_name"));
                    row.put("firstName", rs.getString("fname"));
                    row.put("lastName", rs.getString("lname"));
                    row.put("email", rs.getString("email"));
                    row.put("isGabbai", rs.getInt("is_gabbai") == 1);
                    result.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching participants for series", e);
        }

        return result;
    }

    /**
     * Removes a participant from a series and all associated tables.
     * This removes entries from: gabbaim, shiur_participants, subscribers, favorite_shiurim.
     *
     * @param userId the user ID to remove
     * @param seriesId the series ID
     * @throws RuntimeException if a database error occurs
     */
    public void removeParticipantFromSeries(Long userId, Long seriesId) {
        String deleteGabbai = "DELETE FROM gabbaim WHERE user_id = ? AND series_id = ?";
        String deleteSubscribers = "DELETE FROM subscribers WHERE user_id = ? AND series_id = ?";
        String deleteFavorites = "DELETE FROM favorite_shiurim WHERE user_id = ? AND series_id = ?";
        String deleteParticipant = "DELETE FROM shiur_participants WHERE user_id = ? AND series_id = ?";

        try (Connection conn = dataSource.getConnection()) {
            // Execute all deletions within a single connection for consistency

            // Delete from gabbaim (if exists)
            try (PreparedStatement stmt = conn.prepareStatement(deleteGabbai)) {
                stmt.setLong(1, userId);
                stmt.setLong(2, seriesId);
                stmt.executeUpdate();
            }

            // Delete from subscribers (if exists)
            try (PreparedStatement stmt = conn.prepareStatement(deleteSubscribers)) {
                stmt.setLong(1, userId);
                stmt.setLong(2, seriesId);
                stmt.executeUpdate();
            }

            // Delete from favorite_shiurim (if exists)
            try (PreparedStatement stmt = conn.prepareStatement(deleteFavorites)) {
                stmt.setLong(1, userId);
                stmt.setLong(2, seriesId);
                stmt.executeUpdate();
            }

            // Delete from shiur_participants
            try (PreparedStatement stmt = conn.prepareStatement(deleteParticipant)) {
                stmt.setLong(1, userId);
                stmt.setLong(2, seriesId);
                stmt.executeUpdate();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error removing participant from series", e);
        }
    }

    /**
     * Checks if a participant exists in the series.
     *
     * @param userId the user ID
     * @param seriesId the series ID
     * @return true if the user is a participant, false otherwise
     * @throws RuntimeException if a database error occurs
     */
    public boolean isParticipantInSeries(Long userId, Long seriesId) {
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
            throw new RuntimeException("Error checking if user is participant in series", e);
        }

        return false;
    }
}