package springContents.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for Recording entities.
 * Handles database operations related to shiur recordings including creation,
 * updates, and retrieval of recordings for series.
 */
@Repository
public class RecordingDAO {

    private final DataSource dataSource;

    /**
     * Constructs a new RecordingDAO with the specified data source.
     *
     * @param dataSource the data source for database connections
     */
    @Autowired
    public RecordingDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Creates a new recording in the database.
     *
     * @param seriesId the ID of the parent series
     * @param s3FilePath the S3 file path for the recording
     * @param recordedAt the date and time when the recording was made
     * @param title the title of the recording
     * @param keyword1 the first keyword
     * @param keyword2 the second keyword
     * @param keyword3 the third keyword
     * @param keyword4 the fourth keyword
     * @param keyword5 the fifth keyword
     * @param keyword6 the sixth keyword
     * @param description the description of the recording
     * @return the generated recording ID
     * @throws RuntimeException if a database error occurs
     */
    public long createRecording(Long seriesId,
                                String s3FilePath,
                                LocalDateTime recordedAt,
                                String title,
                                String keyword1,
                                String keyword2,
                                String keyword3,
                                String keyword4,
                                String keyword5,
                                String keyword6,
                                String description) {
        String sql = "INSERT INTO shiur_recordings (series_id, s3_file_path, title, recorded_at, " +
                "keyword_1, keyword_2, keyword_3, keyword_4, keyword_5, keyword_6, description) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, seriesId);
            stmt.setString(2, s3FilePath);
            stmt.setString(3, title);
            stmt.setTimestamp(4, Timestamp.valueOf(recordedAt));
            stmt.setString(5, keyword1);
            stmt.setString(6, keyword2);
            stmt.setString(7, keyword3);
            stmt.setString(8, keyword4);
            stmt.setString(9, keyword5);
            stmt.setString(10, keyword6);
            if (description != null) {
                stmt.setString(11, description);
            } else {
                stmt.setNull(11, Types.VARCHAR);
            }

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Creating recording failed, no rows affected.");
            }

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                } else {
                    throw new SQLException("Creating recording failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creating recording", e);
        }
    }

    /**
     * Updates the S3 file path for a recording.
     *
     * @param recordingId the ID of the recording to update
     * @param s3FilePath the new S3 file path
     * @throws RuntimeException if a database error occurs or no rows are affected
     */
    public void updateS3FilePath(Long recordingId, String s3FilePath) {
        String sql = "UPDATE shiur_recordings SET s3_file_path = ? WHERE recording_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, s3FilePath);
            stmt.setLong(2, recordingId);

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Updating s3_file_path failed, no rows affected.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error updating s3_file_path for recording", e);
        }
    }

    /**
     * Get all recordings for a series
     * @param seriesId The series ID
     * @param sortOrder Sort order: "newest", "oldest", or "title"
     * @return List of recording maps with recordingId, title, recordedAt, description, s3FilePath
     */
    public List<Map<String, Object>> getRecordingsForSeries(Long seriesId, String sortOrder) {
        String orderByClause = switch (sortOrder) {
            case "oldest" -> "ORDER BY recorded_at ASC";
            case "title" -> "ORDER BY title ASC";
            default -> "ORDER BY recorded_at DESC";
        };

        String sql = "SELECT recording_id, title, recorded_at, description, s3_file_path " +
                "FROM shiur_recordings " +
                "WHERE series_id = ? " +
                orderByClause;

        List<Map<String, Object>> recordings = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, seriesId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> recording = new HashMap<>();
                    recording.put("recordingId", rs.getLong("recording_id"));
                    recording.put("title", rs.getString("title"));
                    recording.put("recordedAt", rs.getTimestamp("recorded_at").toLocalDateTime());
                    recording.put("description", rs.getString("description"));
                    recording.put("s3FilePath", rs.getString("s3_file_path"));
                    recordings.add(recording);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching recordings for series", e);
        }

        return recordings;
    }
}