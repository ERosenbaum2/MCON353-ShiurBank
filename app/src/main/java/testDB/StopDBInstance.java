package testDB;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.RdsException;
import software.amazon.awssdk.services.rds.model.StopDbInstanceRequest;
import software.amazon.awssdk.services.rds.model.StopDbInstanceResponse;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Utility class for stopping an AWS RDS database instance.
 * Reads database instance identifier from properties file and stops the instance.
 */
public class StopDBInstance {
    private static final Logger logger = LoggerFactory.getLogger(StopDBInstance.class);

    /**
     * Main method to stop the RDS database instance.
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

        // Create the RDS client
        try (RdsClient rdsClient = RdsClient.builder()
                .region(region)
                .build()) {

            logger.info("Created RDS API Client instance");

            stopSpecificDBInstance(rdsClient, dbInstanceIdentifier);
        }
    }

    /**
     * Stops the specified RDS database instance.
     *
     * @param rdsClient the RDS client to use
     * @param dbInstanceIdentifier the database instance identifier to stop
     * @throws RuntimeException if stopping the instance fails
     */
    public static void stopSpecificDBInstance(RdsClient rdsClient, String dbInstanceIdentifier) {
        try {
            StopDbInstanceRequest stopDbInstanceRequest = StopDbInstanceRequest.builder()
                    .dbInstanceIdentifier(dbInstanceIdentifier)
                    .build();

            StopDbInstanceResponse response = rdsClient.stopDBInstance(stopDbInstanceRequest);
            logger.info("DB Instance {} is now in status: {}", response.dbInstance().dbInstanceIdentifier(), response.dbInstance().dbInstanceStatus());

        } catch (RdsException e) {
            logger.error(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }
}