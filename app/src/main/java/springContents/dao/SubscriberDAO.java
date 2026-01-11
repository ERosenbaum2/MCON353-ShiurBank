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
 * Data Access Object for Subscriber entities.
 * Handles database operations related to SNS subscriptions for series notifications,
 * including subscription management, type retrieval, and subscriber queries.
 */
@Repository
public class SubscriberDAO {

    private final DataSource dataSource;

    /**
     * Constructs a new SubscriberDAO with the specified data source.
     *
     * @param dataSource the data source for database connections
     */
    @Autowired
    public SubscriberDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Retrieves all subscriber types from the database.
     *
     * @return a list of subscriber type maps with typeId and name
     * @throws RuntimeException if a database error occurs
     */
    public List<Map<String, Object>> getAllSubscriberTypes() {
        String sql = "SELECT type_id, name FROM subscriber_types ORDER BY type_id";
        List<Map<String, Object>> types = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> type = new HashMap<>();
                type.put("typeId", rs.getLong("type_id"));
                type.put("name", rs.getString("name"));
                types.add(type);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching subscriber types", e);
        }

        return types;
    }

    /**
     * Checks if a user is subscribed to a series with a specific subscription type.
     *
     * @param userId the user ID
     * @param seriesId the series ID
     * @param subscriptionTypeId the subscription type ID
     * @return true if subscribed, false otherwise
     * @throws RuntimeException if a database error occurs
     */
    public boolean isUserSubscribed(Long userId, Long seriesId, Long subscriptionTypeId) {
        String sql = "SELECT COUNT(*) FROM subscribers " +
                "WHERE user_id = ? AND series_id = ? AND subscription_type_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            stmt.setLong(2, seriesId);
            stmt.setLong(3, subscriptionTypeId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking subscription status", e);
        }

        return false;
    }

    /**
     * Retrieves a user's subscription for a series, if any.
     *
     * @param userId the user ID
     * @param seriesId the series ID
     * @return a map with subscription details, or null if not subscribed
     * @throws RuntimeException if a database error occurs
     */
    public Map<String, Object> getUserSubscription(Long userId, Long seriesId) {
        String sql = "SELECT s.subscriber_id, s.subscription_type_id, s.sns_subscription_arn, " +
                "       st.name AS type_name " +
                "FROM subscribers s " +
                "JOIN subscriber_types st ON s.subscription_type_id = st.type_id " +
                "WHERE s.user_id = ? AND s.series_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            stmt.setLong(2, seriesId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> subscription = new HashMap<>();
                    subscription.put("subscriberId", rs.getLong("subscriber_id"));
                    subscription.put("subscriptionTypeId", rs.getLong("subscription_type_id"));
                    subscription.put("snsSubscriptionArn", rs.getString("sns_subscription_arn"));
                    subscription.put("typeName", rs.getString("type_name"));
                    return subscription;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching user subscription", e);
        }

        return null;
    }

    /**
     * Adds a new subscription for a user to a series.
     *
     * @param userId the user ID
     * @param seriesId the series ID
     * @param subscriptionTypeId the subscription type ID
     * @param snsSubscriptionArn the SNS subscription ARN
     * @return the generated subscriber ID
     * @throws RuntimeException if a database error occurs or subscription creation fails
     */
    public long addSubscription(Long userId, Long seriesId, Long subscriptionTypeId, String snsSubscriptionArn) {
        String sql = "INSERT INTO subscribers (user_id, series_id, subscription_type_id, sns_subscription_arn) " +
                "VALUES (?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, userId);
            stmt.setLong(2, seriesId);
            stmt.setLong(3, subscriptionTypeId);
            stmt.setString(4, snsSubscriptionArn);

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Adding subscription failed, no rows affected.");
            }

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                } else {
                    throw new SQLException("Adding subscription failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error adding subscription", e);
        }
    }

    /**
     * Removes a subscription for a user from a series.
     *
     * @param userId the user ID
     * @param seriesId the series ID
     * @throws RuntimeException if a database error occurs
     */
    public void removeSubscription(Long userId, Long seriesId) {
        String sql = "DELETE FROM subscribers WHERE user_id = ? AND series_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            stmt.setLong(2, seriesId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error removing subscription", e);
        }
    }

    /**
     * Updates the SNS subscription ARN for a user's subscription.
     *
     * @param userId the user ID
     * @param seriesId the series ID
     * @param snsSubscriptionArn the new subscription ARN
     * @throws RuntimeException if a database error occurs or update fails
     */
    public void updateSubscriptionArn(Long userId, Long seriesId, String snsSubscriptionArn) {
        String sql = "UPDATE subscribers SET sns_subscription_arn = ? " +
                "WHERE user_id = ? AND series_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, snsSubscriptionArn);
            stmt.setLong(2, userId);
            stmt.setLong(3, seriesId);

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Updating subscription ARN failed, no rows affected.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error updating subscription ARN", e);
        }
    }

    /**
     * Retrieves all subscribers for a series.
     *
     * @param seriesId the series ID
     * @return a list of subscriber information maps
     * @throws RuntimeException if a database error occurs
     */
    public List<Map<String, Object>> getSeriesSubscribers(Long seriesId) {
        String sql = "SELECT s.subscriber_id, s.user_id, s.subscription_type_id, " +
                "       s.sns_subscription_arn, u.email, " +
                "       CONCAT(u.title, ' ', u.fname, ' ', u.lname) AS full_name, " +
                "       st.name AS type_name " +
                "FROM subscribers s " +
                "JOIN users u ON s.user_id = u.user_id " +
                "JOIN subscriber_types st ON s.subscription_type_id = st.type_id " +
                "WHERE s.series_id = ?";

        List<Map<String, Object>> subscribers = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, seriesId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> subscriber = new HashMap<>();
                    subscriber.put("subscriberId", rs.getLong("subscriber_id"));
                    subscriber.put("userId", rs.getLong("user_id"));
                    subscriber.put("subscriptionTypeId", rs.getLong("subscription_type_id"));
                    subscriber.put("snsSubscriptionArn", rs.getString("sns_subscription_arn"));
                    subscriber.put("email", rs.getString("email"));
                    subscriber.put("fullName", rs.getString("full_name"));
                    subscriber.put("typeName", rs.getString("type_name"));
                    subscribers.add(subscriber);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching series subscribers", e);
        }

        return subscribers;
    }
}