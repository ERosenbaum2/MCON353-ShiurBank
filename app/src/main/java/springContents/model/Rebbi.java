package springContents.model;

/**
 * Represents a Rebbi (teacher) who delivers shiurim.
 * Contains the Rebbi's identification information including title, first name,
 * last name, and optionally a linked user account.
 */
public class Rebbi {

    private Long rebbiId;
    private String title;
    private String fname;
    private String lname;
    private Long userId; // may be null

    /**
     * Gets the Rebbi's unique identifier.
     *
     * @return the Rebbi ID
     */
    public Long getRebbiId() {
        return rebbiId;
    }

    /**
     * Sets the Rebbi's unique identifier.
     *
     * @param rebbiId the Rebbi ID to set
     */
    public void setRebbiId(Long rebbiId) {
        this.rebbiId = rebbiId;
    }

    /**
     * Gets the Rebbi's title.
     *
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the Rebbi's title.
     *
     * @param title the title to set
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Gets the Rebbi's first name.
     *
     * @return the first name
     */
    public String getFname() {
        return fname;
    }

    /**
     * Sets the Rebbi's first name.
     *
     * @param fname the first name to set
     */
    public void setFname(String fname) {
        this.fname = fname;
    }

    /**
     * Gets the Rebbi's last name.
     *
     * @return the last name
     */
    public String getLname() {
        return lname;
    }

    /**
     * Sets the Rebbi's last name.
     *
     * @param lname the last name to set
     */
    public void setLname(String lname) {
        this.lname = lname;
    }

    /**
     * Gets the associated user ID, if the Rebbi is linked to a user account.
     *
     * @return the user ID, or null if not linked
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Sets the associated user ID.
     *
     * @param userId the user ID to set, or null to unlink
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }
}