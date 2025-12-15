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

@Configuration
@EnableTransactionManagement  // Add this annotation
public class DatabaseConfig {

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

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }
}