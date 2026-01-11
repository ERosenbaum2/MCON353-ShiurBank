package springContents.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import springContents.model.Institution;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Institution entities.
 * Handles database operations related to institutions including retrieval
 * of all institutions and lookup by ID.
 */
@Repository
public class InstitutionDAO {
    
    private final DataSource dataSource;
    
    /**
     * Constructs a new InstitutionDAO with the specified data source.
     *
     * @param dataSource the data source for database connections
     */
    @Autowired
    public InstitutionDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    /**
     * Retrieves all institutions from the database, ordered by name.
     *
     * @return a list of all institutions
     * @throws RuntimeException if a database error occurs
     */
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
    
    /**
     * Retrieves an institution by its unique identifier.
     *
     * @param instId the institution ID to look up
     * @return the Institution object, or null if not found
     * @throws RuntimeException if a database error occurs
     */
    public Institution getInstitutionById(Long instId) {
        String sql = "SELECT inst_id, name FROM institutions WHERE inst_id = ?";
        
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

