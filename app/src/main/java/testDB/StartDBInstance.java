package testDB;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.RdsException;
import software.amazon.awssdk.services.rds.model.StartDbInstanceRequest;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Utility class for starting an AWS RDS database instance.
 * Reads database instance identifier from properties file and starts the instance.
 */
public class StartDBInstance {
    private static final Logger logger = LoggerFactory.getLogger(StartDBInstance.class);

    /**
     * Main method to start the RDS database instance.
     *
     * @param args command line arguments (not used)
     * @throws IOException if the properties file cannot be read
     */
    public static void main(String[] args) throws IOException {
        Properties credentials = new Properties();
        credentials.load(new FileInputStream("app/src/main/resources/dbcredentials.properties"));
        logger.info("Credentials received");

        final String dbInstanceIdentifier = credentials.getProperty("instance");
        Region region = Region.US_EAST_1;

        RdsClient rdsClient = RdsClient.builder()
                .region(region)
                .build();

        logger.info("Created RDS API Client instance");

        startInstance(rdsClient, dbInstanceIdentifier);
        rdsClient.close();
    }

    /**
     * Starts the specified RDS database instance.
     *
     * @param rdsClient the RDS client to use
     * @param dbInstanceIdentifier the database instance identifier to start
     * @throws RuntimeException if starting the instance fails
     */
    public static void startInstance(RdsClient rdsClient, String dbInstanceIdentifier) {
        logger.info(dbInstanceIdentifier);
        try {
            StartDbInstanceRequest startDbInstanceRequest = StartDbInstanceRequest.builder()
                    .dbInstanceIdentifier(dbInstanceIdentifier)
                    .build();

            rdsClient.startDBInstance(startDbInstanceRequest);
            logger.info("Successfully started RDS instance: {}", dbInstanceIdentifier);

        } catch (RdsException e) {
            logger.error(e.getMessage());
            System.exit(1);
        }
    }
}