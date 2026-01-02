package springContents.model;

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

    public SearchResult() {
    }

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRebbiName() {
        return rebbiName;
    }

    public void setRebbiName(String rebbiName) {
        this.rebbiName = rebbiName;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public void setInstitutionName(String institutionName) {
        this.institutionName = institutionName;
    }

    public String getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(String recordedAt) {
        this.recordedAt = recordedAt;
    }

    public Long getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(Long seriesId) {
        this.seriesId = seriesId;
    }

    public boolean isHasAccess() {
        return hasAccess;
    }

    public void setHasAccess(boolean hasAccess) {
        this.hasAccess = hasAccess;
    }

    public int getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(int relevanceScore) {
        this.relevanceScore = relevanceScore;
    }
}