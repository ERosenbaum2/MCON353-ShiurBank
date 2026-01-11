package springContents.model;

/**
 * Represents a search result item that can be either a shiur series or
 * an individual recording. Contains metadata about the result including
 * title, description, Rebbi, topic, institution, access status, and relevance score.
 */
public class SearchResult {
    private String type; // "SERIES" or "RECORDING"
    private Long id;
    private String title;
    private String description;
    private String rebbiName;
    private String topicName;
    private String institutionName;
    private String recordedAt; // For recordings only
    private Long seriesId; // For recordings - links to parent series
    private boolean hasAccess;
    private int relevanceScore;
    private boolean hasPendingApplication;

    /**
     * Default constructor for SearchResult.
     */
    public SearchResult() {
    }

    // Getters and Setters
    /**
     * Gets the result type, either "SERIES" or "RECORDING".
     *
     * @return the result type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the result type.
     *
     * @param type the type to set, either "SERIES" or "RECORDING"
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Gets the unique identifier of the series or recording.
     *
     * @return the ID
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the unique identifier.
     *
     * @param id the ID to set
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Gets the title of the series or recording.
     *
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the title.
     *
     * @param title the title to set
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Gets the description of the series or recording.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the name of the Rebbi who delivers this series or recording.
     *
     * @return the Rebbi name
     */
    public String getRebbiName() {
        return rebbiName;
    }

    /**
     * Sets the Rebbi name.
     *
     * @param rebbiName the Rebbi name to set
     */
    public void setRebbiName(String rebbiName) {
        this.rebbiName = rebbiName;
    }

    /**
     * Gets the topic name associated with this series or recording.
     *
     * @return the topic name
     */
    public String getTopicName() {
        return topicName;
    }

    /**
     * Sets the topic name.
     *
     * @param topicName the topic name to set
     */
    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    /**
     * Gets the institution name associated with this series or recording.
     *
     * @return the institution name
     */
    public String getInstitutionName() {
        return institutionName;
    }

    /**
     * Sets the institution name.
     *
     * @param institutionName the institution name to set
     */
    public void setInstitutionName(String institutionName) {
        this.institutionName = institutionName;
    }

    /**
     * Gets the recording date/time, applicable only for recording results.
     *
     * @return the recorded date/time as a string
     */
    public String getRecordedAt() {
        return recordedAt;
    }

    /**
     * Sets the recording date/time.
     *
     * @param recordedAt the recorded date/time to set
     */
    public void setRecordedAt(String recordedAt) {
        this.recordedAt = recordedAt;
    }

    /**
     * Gets the parent series ID, applicable only for recording results.
     *
     * @return the series ID
     */
    public Long getSeriesId() {
        return seriesId;
    }

    /**
     * Sets the parent series ID.
     *
     * @param seriesId the series ID to set
     */
    public void setSeriesId(Long seriesId) {
        this.seriesId = seriesId;
    }

    /**
     * Checks if the current user has access to this series or recording.
     *
     * @return true if the user has access, false otherwise
     */
    public boolean isHasAccess() {
        return hasAccess;
    }

    /**
     * Sets the access status for the current user.
     *
     * @param hasAccess true if the user has access, false otherwise
     */
    public void setHasAccess(boolean hasAccess) {
        this.hasAccess = hasAccess;
    }

    /**
     * Gets the relevance score calculated for this search result.
     *
     * @return the relevance score
     */
    public int getRelevanceScore() {
        return relevanceScore;
    }

    /**
     * Sets the relevance score.
     *
     * @param relevanceScore the relevance score to set
     */
    public void setRelevanceScore(int relevanceScore) {
        this.relevanceScore = relevanceScore;
    }

    /**
     * Checks if the current user has a pending application for this series.
     *
     * @return true if there is a pending application, false otherwise
     */
    public boolean isHasPendingApplication() {
        return hasPendingApplication;
    }

    /**
     * Sets the pending application status.
     *
     * @param hasPendingApplication true if there is a pending application, false otherwise
     */
    public void setHasPendingApplication(boolean hasPendingApplication) {
        this.hasPendingApplication = hasPendingApplication;
    }
}