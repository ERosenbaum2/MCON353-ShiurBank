package springContents.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import springContents.model.SearchResult;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
public class SearchDAO {

    private final DataSource dataSource;

    @Autowired
    public SearchDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Search for series matching the query terms
     */
    public List<SearchResult> searchSeries(Set<String> keywords, Set<String> rebbiNames,
                                           Set<String> topicNames, Set<String> institutionNames,
                                           Long userId) {
        List<SearchResult> results = new ArrayList<>();

        // Build dynamic SQL based on search criteria
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT ss.series_id, ss.description, ");
        sql.append("CONCAT(r.title, ' ', r.fname, ' ', r.lname) AS rebbi_name, ");
        sql.append("t.name AS topic_name, ");
        sql.append("i.name AS institution_name, ");
        sql.append("EXISTS(SELECT 1 FROM shiur_participants sp WHERE sp.series_id = ss.series_id AND sp.user_id = ?) AS has_access ");
        sql.append("FROM shiur_series ss ");
        sql.append("JOIN rebbeim r ON ss.rebbi_id = r.rebbi_id ");
        sql.append("JOIN topics t ON ss.topic_id = t.topic_id ");
        sql.append("JOIN institutions i ON ss.inst_id = i.inst_id ");
        sql.append("WHERE (ss.requires_permission = FALSE OR EXISTS(SELECT 1 FROM shiur_participants sp2 WHERE sp2.series_id = ss.series_id AND sp2.user_id = ?)) ");

        List<String> conditions = new ArrayList<>();
        List<String> params = new ArrayList<>();

        // Add keyword search conditions
        if (!keywords.isEmpty()) {
            List<String> keywordConditions = new ArrayList<>();
            for (String keyword : keywords) {
                keywordConditions.add("(ss.description LIKE ? OR t.name LIKE ? OR i.name LIKE ? OR " +
                        "CONCAT(r.title, ' ', r.fname, ' ', r.lname) LIKE ?)");
                String likePattern = "%" + keyword + "%";
                params.add(likePattern);
                params.add(likePattern);
                params.add(likePattern);
                params.add(likePattern);
            }
            conditions.add("(" + String.join(" OR ", keywordConditions) + ")");
        }

        // Add rebbi name search
        if (!rebbiNames.isEmpty()) {
            List<String> rebbiConditions = new ArrayList<>();
            for (String name : rebbiNames) {
                rebbiConditions.add("CONCAT(r.title, ' ', r.fname, ' ', r.lname) LIKE ?");
                params.add("%" + name + "%");
            }
            conditions.add("(" + String.join(" OR ", rebbiConditions) + ")");
        }

        // Add topic search
        if (!topicNames.isEmpty()) {
            List<String> topicConditions = new ArrayList<>();
            for (String topic : topicNames) {
                topicConditions.add("t.name LIKE ?");
                params.add("%" + topic + "%");
            }
            conditions.add("(" + String.join(" OR ", topicConditions) + ")");
        }

        // Add institution search
        if (!institutionNames.isEmpty()) {
            List<String> instConditions = new ArrayList<>();
            for (String inst : institutionNames) {
                instConditions.add("i.name LIKE ?");
                params.add("%" + inst + "%");
            }
            conditions.add("(" + String.join(" OR ", instConditions) + ")");
        }

        if (!conditions.isEmpty()) {
            sql.append("AND (").append(String.join(" OR ", conditions)).append(") ");
        }

        sql.append("ORDER BY has_access DESC");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            stmt.setLong(paramIndex++, userId);
            stmt.setLong(paramIndex++, userId);

            for (String param : params) {
                stmt.setString(paramIndex++, param);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SearchResult result = new SearchResult();
                    result.setType("SERIES");
                    result.setId(rs.getLong("series_id"));
                    result.setDescription(rs.getString("description"));
                    result.setRebbiName(rs.getString("rebbi_name"));
                    result.setTopicName(rs.getString("topic_name"));
                    result.setInstitutionName(rs.getString("institution_name"));
                    result.setHasAccess(rs.getBoolean("has_access"));
                    results.add(result);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error searching series", e);
        }

        return results;
    }

    /**
     * Search for recordings matching the query terms
     */
    public List<SearchResult> searchRecordings(Set<String> keywords, Set<String> rebbiNames,
                                               Set<String> topicNames, Set<String> institutionNames,
                                               Long userId) {
        List<SearchResult> results = new ArrayList<>();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT sr.recording_id, sr.series_id, sr.title, sr.description, sr.recorded_at, ");
        sql.append("sr.keyword_1, sr.keyword_2, sr.keyword_3, sr.keyword_4, sr.keyword_5, sr.keyword_6, ");
        sql.append("CONCAT(r.title, ' ', r.fname, ' ', r.lname) AS rebbi_name, ");
        sql.append("t.name AS topic_name, ");
        sql.append("i.name AS institution_name, ");
        sql.append("EXISTS(SELECT 1 FROM shiur_participants sp WHERE sp.series_id = ss.series_id AND sp.user_id = ?) AS has_access ");
        sql.append("FROM shiur_recordings sr ");
        sql.append("JOIN shiur_series ss ON sr.series_id = ss.series_id ");
        sql.append("JOIN rebbeim r ON ss.rebbi_id = r.rebbi_id ");
        sql.append("JOIN topics t ON ss.topic_id = t.topic_id ");
        sql.append("JOIN institutions i ON ss.inst_id = i.inst_id ");
        sql.append("WHERE (ss.requires_permission = FALSE OR EXISTS(SELECT 1 FROM shiur_participants sp2 WHERE sp2.series_id = ss.series_id AND sp2.user_id = ?)) ");

        List<String> conditions = new ArrayList<>();
        List<String> params = new ArrayList<>();

        // Add keyword search conditions
        if (!keywords.isEmpty()) {
            List<String> keywordConditions = new ArrayList<>();
            for (String keyword : keywords) {
                keywordConditions.add("(sr.title LIKE ? OR sr.description LIKE ? OR " +
                        "sr.keyword_1 LIKE ? OR sr.keyword_2 LIKE ? OR " +
                        "sr.keyword_3 LIKE ? OR sr.keyword_4 LIKE ? OR " +
                        "sr.keyword_5 LIKE ? OR sr.keyword_6 LIKE ? OR " +
                        "t.name LIKE ? OR i.name LIKE ? OR " +
                        "CONCAT(r.title, ' ', r.fname, ' ', r.lname) LIKE ?)");
                String likePattern = "%" + keyword + "%";
                for (int i = 0; i < 11; i++) {
                    params.add(likePattern);
                }
            }
            conditions.add("(" + String.join(" OR ", keywordConditions) + ")");
        }

        // Add rebbi name search
        if (!rebbiNames.isEmpty()) {
            List<String> rebbiConditions = new ArrayList<>();
            for (String name : rebbiNames) {
                rebbiConditions.add("CONCAT(r.title, ' ', r.fname, ' ', r.lname) LIKE ?");
                params.add("%" + name + "%");
            }
            conditions.add("(" + String.join(" OR ", rebbiConditions) + ")");
        }

        // Add topic search
        if (!topicNames.isEmpty()) {
            List<String> topicConditions = new ArrayList<>();
            for (String topic : topicNames) {
                topicConditions.add("t.name LIKE ?");
                params.add("%" + topic + "%");
            }
            conditions.add("(" + String.join(" OR ", topicConditions) + ")");
        }

        // Add institution search
        if (!institutionNames.isEmpty()) {
            List<String> instConditions = new ArrayList<>();
            for (String inst : institutionNames) {
                instConditions.add("i.name LIKE ?");
                params.add("%" + inst + "%");
            }
            conditions.add("(" + String.join(" OR ", instConditions) + ")");
        }

        if (!conditions.isEmpty()) {
            sql.append("AND (").append(String.join(" OR ", conditions)).append(") ");
        }

        sql.append("ORDER BY has_access DESC, sr.recorded_at DESC");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            stmt.setLong(paramIndex++, userId);
            stmt.setLong(paramIndex++, userId);

            for (String param : params) {
                stmt.setString(paramIndex++, param);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SearchResult result = new SearchResult();
                    result.setType("RECORDING");
                    result.setId(rs.getLong("recording_id"));
                    result.setSeriesId(rs.getLong("series_id"));
                    result.setTitle(rs.getString("title"));
                    result.setDescription(rs.getString("description"));
                    result.setRecordedAt(rs.getString("recorded_at"));
                    result.setRebbiName(rs.getString("rebbi_name"));
                    result.setTopicName(rs.getString("topic_name"));
                    result.setInstitutionName(rs.getString("institution_name"));
                    result.setHasAccess(rs.getBoolean("has_access"));
                    results.add(result);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error searching recordings", e);
        }

        return results;
    }

    /**
     * Get all rebbi names for matching
     */
    public List<String> getAllRebbiNames() {
        List<String> names = new ArrayList<>();
        String sql = "SELECT DISTINCT CONCAT(title, ' ', fname, ' ', lname) AS full_name FROM rebbeim";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("full_name").toLowerCase());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching rebbi names", e);
        }

        return names;
    }

    /**
     * Get all topic names for matching
     */
    public List<String> getAllTopicNames() {
        List<String> names = new ArrayList<>();
        String sql = "SELECT DISTINCT name FROM topics";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("name").toLowerCase());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching topic names", e);
        }

        return names;
    }

    /**
     * Get all institution names for matching
     */
    public List<String> getAllInstitutionNames() {
        List<String> names = new ArrayList<>();
        String sql = "SELECT DISTINCT name FROM institutions";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("name").toLowerCase());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching institution names", e);
        }

        return names;
    }

    /**
     * Get user's associated institution IDs
     */
    public List<Long> getUserInstitutions(Long userId) {
        List<Long> institutionIds = new ArrayList<>();
        String sql = "SELECT inst_id FROM user_institution_assoc WHERE user_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    institutionIds.add(rs.getLong("inst_id"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching user institutions", e);
        }

        return institutionIds;
    }
}