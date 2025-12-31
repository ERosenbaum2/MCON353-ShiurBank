package springContents.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;

@Repository
public class RecordingDAO {

    private final DataSource dataSource;

    @Autowired
    public RecordingDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

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
}