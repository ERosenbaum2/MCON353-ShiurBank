package springContents.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import springContents.model.Institution;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class InstitutionDAO {
    
    private final DataSource dataSource;
    
    @Autowired
    public InstitutionDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    public List<Institution> getAllInstitutions() {
        List<Institution> institutions = new ArrayList<>();
        String sql = "SELECT inst_id, name FROM institutions ORDER BY name";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Institution institution = new Institution();
                institution.setInstId(rs.getLong("inst_id"));
                institution.setName(rs.getString("name"));
                institutions.add(institution);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching institutions", e);
        }
        
        return institutions;
    }
    
    public Institution getInstitutionById(Long instId) {
        String sql = "SELECT inst_id, name FROM institution WHERE inst_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, instId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Institution institution = new Institution();
                    institution.setInstId(rs.getLong("inst_id"));
                    institution.setName(rs.getString("name"));
                    return institution;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching institution by ID", e);
        }
        return null;
    }
}

