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
                               String keyword1,
                               String keyword2,
                               String keyword3,
                               String keyword4,
                               String keyword5,
                               String keyword6,
                               String description) {
        String sql = "INSERT INTO shiur_recordings (series_id, s3_file_path, recorded_at, " +
                     "keyword_1, keyword_2, keyword_3, keyword_4, keyword_5, keyword_6, description) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, seriesId);
            stmt.setString(2, s3FilePath);
            stmt.setTimestamp(3, Timestamp.valueOf(recordedAt));
            stmt.setString(4, keyword1);
            stmt.setString(5, keyword2);
            stmt.setString(6, keyword3);
            stmt.setString(7, keyword4);
            stmt.setString(8, keyword5);
            stmt.setString(9, keyword6);
            if (description != null) {
                stmt.setString(10, description);
            } else {
                stmt.setNull(10, Types.VARCHAR);
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

    public List<Map<String, Object>> searchRecordings(String query, Long userId) {
        String searchPattern = "%" + query.toLowerCase() + "%";
        String sql =
                "SELECT DISTINCT sr.recording_id, sr.series_id, sr.description AS recording_description, " +
                        "       sr.keyword_1, sr.keyword_2, sr.keyword_3, sr.keyword_4, sr.keyword_5, sr.keyword_6, " +
                        "       sr.recorded_at, " +
                        "       s.description AS series_description, s.requires_permission, " +
                        "       t.name AS topic_name, " +
                        "       CONCAT(r.title, ' ', r.fname, ' ', r.lname) AS rebbi_name, " +
                        "       i.name AS inst_name, " +
                        "       CASE WHEN (sp.user_id IS NOT NULL OR g.user_id IS NOT NULL) THEN 1 ELSE 0 END AS has_access " +
                        "FROM shiur_recordings sr " +
                        "JOIN shiur_series s ON sr.series_id = s.series_id " +
                        "JOIN topics t ON s.topic_id = t.topic_id " +
                        "JOIN rebbeim r ON s.rebbi_id = r.rebbi_id " +
                        "JOIN institutions i ON s.inst_id = i.inst_id " +
                        "LEFT JOIN shiur_participants sp ON s.series_id = sp.series_id AND sp.user_id = ? " +
                        "LEFT JOIN gabbaim g ON s.series_id = g.series_id AND g.user_id = ? " +
                        "WHERE LOWER(sr.keyword_1) LIKE ? " +
                        "   OR LOWER(sr.keyword_2) LIKE ? " +
                        "   OR LOWER(sr.keyword_3) LIKE ? " +
                        "   OR LOWER(sr.keyword_4) LIKE ? " +
                        "   OR LOWER(sr.keyword_5) LIKE ? " +
                        "   OR LOWER(sr.keyword_6) LIKE ? " +
                        "   OR LOWER(sr.description) LIKE ? " +
                        "ORDER BY has_access DESC, sr.recorded_at DESC";

        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            stmt.setLong(2, userId);
            stmt.setString(3, searchPattern);
            stmt.setString(4, searchPattern);
            stmt.setString(5, searchPattern);
            stmt.setString(6, searchPattern);
            stmt.setString(7, searchPattern);
            stmt.setString(8, searchPattern);
            stmt.setString(9, searchPattern);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    long recordingId = rs.getLong("recording_id");
                    long seriesId = rs.getLong("series_id");
                    String topicName = rs.getString("topic_name");
                    String rebbiName = rs.getString("rebbi_name");
                    boolean hasAccess = rs.getInt("has_access") == 1;
                    boolean requiresPermission = rs.getBoolean("requires_permission");

                    // Build keywords list
                    List<String> keywords = new ArrayList<>();
                    for (int i = 1; i <= 6; i++) {
                        String keyword = rs.getString("keyword_" + i);
                        if (keyword != null && !keyword.trim().isEmpty()) {
                            keywords.add(keyword.trim());
                        }
                    }

                    row.put("recordingId", recordingId);
                    row.put("seriesId", seriesId);
                    row.put("recordingDescription", rs.getString("recording_description"));
                    row.put("keywords", keywords);
                    row.put("recordedAt", rs.getTimestamp("recorded_at") != null 
                            ? rs.getTimestamp("recorded_at").toString() : null);
                    row.put("seriesDescription", rs.getString("series_description"));
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
            throw new RuntimeException("Error searching recordings", e);
        }

        return result;
    }
}

