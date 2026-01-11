package springContents.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import springContents.model.Topic;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Topic entities.
 * Handles database operations related to topic retrieval and management.
 */
@Repository
public class TopicDAO {

    private final DataSource dataSource;

    /**
     * Constructs a new TopicDAO with the specified data source.
     *
     * @param dataSource the data source for database connections
     */
    @Autowired
    public TopicDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Retrieves all topics from the database, ordered by name.
     *
     * @return a list of all topics
     * @throws RuntimeException if a database error occurs
     */
    public List<Topic> getAllTopics() {
        List<Topic> topics = new ArrayList<>();
        String sql = "SELECT topic_id, name FROM topics ORDER BY name";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Topic topic = new Topic();
                topic.setTopicId(rs.getLong("topic_id"));
                topic.setName(rs.getString("name"));
                topics.add(topic);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching topics", e);
        }

        return topics;
    }
}


