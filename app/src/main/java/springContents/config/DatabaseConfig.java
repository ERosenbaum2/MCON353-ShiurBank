package springContents.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

/**
 * Configuration class for database connection and transaction management.
 * Sets up the MySQL data source using credentials from dbcredentials.properties
 * and configures Spring transaction management.
 */
@Configuration
@EnableTransactionManagement  // Add this annotation
public class DatabaseConfig {

    /**
     * Creates and configures the MySQL data source bean.
     * Reads database connection properties from dbcredentials.properties file.
     *
     * @return the configured DataSource
     * @throws IOException if the properties file cannot be read
     */
    @Bean
    public DataSource dataSource() throws IOException {
        Properties credentials = new Properties();
        credentials.load(new FileInputStream("dbcredentials.properties"));

        String endpoint = credentials.getProperty("db_connection");
        String database = credentials.getProperty("database");
        String username = credentials.getProperty("user");
        String password = credentials.getProperty("password");

        String connectionUrl = "jdbc:mysql://" + endpoint + "/" + database
                + "?useSSL=true"
                + "&serverTimezone=UTC";

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(connectionUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        return dataSource;
    }

    /**
     * Creates and configures the transaction manager bean.
     *
     * @param dataSource the data source to use for transactions
     * @return the configured PlatformTransactionManager
     * @throws NullPointerException if dataSource is null
     */
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }
}