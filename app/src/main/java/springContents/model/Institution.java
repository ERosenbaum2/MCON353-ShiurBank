package springContents.model;

/**
 * Represents an institution (yeshiva, school, organization) that hosts or
 * sponsors shiur series. Users can be associated with institutions to control
 * access to institution-specific content.
 */
public class Institution {
    private Long instId;
    private String name;
    
    /**
     * Default constructor for Institution.
     */
    public Institution() {
    }
    
    /**
     * Constructs a new Institution with the specified ID and name.
     *
     * @param instId the institution's unique identifier
     * @param name the institution's name
     */
    public Institution(Long instId, String name) {
        this.instId = instId;
        this.name = name;
    }
    
    /**
     * Gets the institution's unique identifier.
     *
     * @return the institution ID
     */
    public Long getInstId() {
        return instId;
    }
    
    /**
     * Sets the institution's unique identifier.
     *
     * @param instId the institution ID to set
     */
    public void setInstId(Long instId) {
        this.instId = instId;
    }
    
    /**
     * Gets the institution's name.
     *
     * @return the institution name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Sets the institution's name.
     *
     * @param name the institution name to set
     */
    public void setName(String name) {
        this.name = name;
    }
}

