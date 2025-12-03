package springContents.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

@Configuration
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
}

