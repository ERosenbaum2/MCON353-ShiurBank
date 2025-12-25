package springContents.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import springContents.model.Rebbi;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class RebbiDAO {

    private final DataSource dataSource;

    @Autowired
    public RebbiDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Rebbi> getAllRebbeim() {
        List<Rebbi> rebbeim = new ArrayList<>();
        String sql = "SELECT rebbi_id, title, fname, lname, user_id FROM rebbeim ORDER BY lname, fname";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Rebbi rebbi = new Rebbi();
                rebbi.setRebbiId(rs.getLong("rebbi_id"));
                rebbi.setTitle(rs.getString("title"));
                rebbi.setFname(rs.getString("fname"));
                rebbi.setLname(rs.getString("lname"));
                Object userIdObj = rs.getObject("user_id");
                rebbi.setUserId(userIdObj != null ? rs.getLong("user_id") : null);
                rebbeim.add(rebbi);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching rebbeim", e);
        }

        return rebbeim;
    }
}