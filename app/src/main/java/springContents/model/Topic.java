package springContents.model;

/**
 * Represents a topic category for shiur series and recordings.
 * Topics are used to categorize and organize shiurim by subject matter.
 */
public class Topic {

    private Long topicId;
    private String name;

    /**
     * Gets the topic's unique identifier.
     *
     * @return the topic ID
     */
    public Long getTopicId() {
        return topicId;
    }

    /**
     * Sets the topic's unique identifier.
     *
     * @param topicId the topic ID to set
     */
    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    /**
     * Gets the topic name.
     *
     * @return the topic name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the topic name.
     *
     * @param name the topic name to set
     */
    public void setName(String name) {
        this.name = name;
    }
}