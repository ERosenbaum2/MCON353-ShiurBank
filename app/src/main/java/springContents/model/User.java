package springContents.model;

/**
 * Represents a user in the ShiurBank system.
 * Contains user authentication information and personal details including
 * username, hashed password, title, name, and email address.
 */
public class User {
    private Long userId;
    private String username;
    private String hashedPassword;
    private String title;
    private String firstName;
    private String lastName;
    private String email;
    
    /**
     * Default constructor for User.
     */
    public User() {
    }
    
    /**
     * Constructs a new User with the specified details.
     *
     * @param username the unique username for the user
     * @param hashedPassword the hashed password for authentication
     * @param title the user's title (e.g., "Rabbi", "Dr.")
     * @param firstName the user's first name
     * @param lastName the user's last name
     * @param email the user's email address
     */
    public User(String username, String hashedPassword, String title, String firstName, String lastName, String email) {
        this.username = username;
        this.hashedPassword = hashedPassword;
        this.title = title;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }
    
    // Getters and Setters
    /**
     * Gets the user's unique identifier.
     *
     * @return the user ID
     */
    public Long getUserId() {
        return userId;
    }
    
    /**
     * Sets the user's unique identifier.
     *
     * @param userId the user ID to set
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    /**
     * Gets the username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }
    
    /**
     * Sets the username.
     *
     * @param username the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }
    
    /**
     * Gets the hashed password.
     *
     * @return the hashed password
     */
    public String getHashedPassword() {
        return hashedPassword;
    }
    
    /**
     * Sets the hashed password.
     *
     * @param hashedPassword the hashed password to set
     */
    public void setHashedPassword(String hashedPassword) {
        this.hashedPassword = hashedPassword;
    }
    
    /**
     * Gets the user's title.
     *
     * @return the title
     */
    public String getTitle() {
        return title;
    }
    
    /**
     * Sets the user's title.
     *
     * @param title the title to set
     */
    public void setTitle(String title) {
        this.title = title;
    }
    
    /**
     * Gets the user's first name.
     *
     * @return the first name
     */
    public String getFirstName() {
        return firstName;
    }
    
    /**
     * Sets the user's first name.
     *
     * @param firstName the first name to set
     */
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    /**
     * Gets the user's last name.
     *
     * @return the last name
     */
    public String getLastName() {
        return lastName;
    }
    
    /**
     * Sets the user's last name.
     *
     * @param lastName the last name to set
     */
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    
    /**
     * Gets the user's email address.
     *
     * @return the email address
     */
    public String getEmail() {
        return email;
    }
    
    /**
     * Sets the user's email address.
     *
     * @param email the email address to set
     */
    public void setEmail(String email) {
        this.email = email;
    }
}

