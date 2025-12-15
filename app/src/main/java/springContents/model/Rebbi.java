package springContents.model;

public class Rebbi {

    private Long rebbiId;
    private String title;
    private String fname;
    private String lname;
    private Long userId; // may be null

    public Long getRebbiId() {
        return rebbiId;
    }

    public void setRebbiId(Long rebbiId) {
        this.rebbiId = rebbiId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFname() {
        return fname;
    }

    public void setFname(String fname) {
        this.fname = fname;
    }

    public String getLname() {
        return lname;
    }

    public void setLname(String lname) {
        this.lname = lname;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}