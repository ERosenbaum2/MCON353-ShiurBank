package testDB;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.Properties;

/**
 * Utility class for testing database connectivity.
 * Connects to the MySQL database and lists all tables in the database.
 */
public class Testdb_conn {
    /**
     * Main method to test database connection and list all tables.
     *
     * @param args command line arguments (not used)
     * @throws IOException if the properties file cannot be read
     */
    public static void main(String[] args) throws IOException {
        Properties credentials = new Properties();
        credentials.load(new FileInputStream("app/src/main/resources/dbcredentials.properties"));
        String endpoint = credentials.getProperty("db_connection");
        String database = credentials.getProperty("database");
        String username = credentials.getProperty("user");
        String password = credentials.getProperty("password");

        String connectionUrl = "jdbc:mysql://" + endpoint + "/" + database
                + "?useSSL=true"
                + "&serverTimezone=UTC";

        try (Connection connection = DriverManager.getConnection(connectionUrl, username, password)){
            DatabaseMetaData metaData = connection.getMetaData();

            // Get all tables
            ResultSet tables = metaData.getTables(database, null, "%", new String[]{"TABLE"});

            System.out.println("Tables in database:");
            System.out.println("==================");

            int count = 0;
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                count++;
                System.out.println(count + ". " + tableName);
            }

            if (count == 0) {
                System.out.println("No tables found in the database.");
            } else {
                System.out.println("\nTotal tables: " + count);
            }

            tables.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
}