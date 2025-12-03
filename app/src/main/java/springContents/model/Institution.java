package springContents.model;

public class Institution {
    private Long instId;
    private String name;
    
    public Institution() {
    }
    
    public Institution(Long instId, String name) {
        this.instId = instId;
        this.name = name;
    }
    
    public Long getInstId() {
        return instId;
    }
    
    public void setInstId(Long instId) {
        this.instId = instId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
}

