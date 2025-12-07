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

public class StopDBInstance {
    private static final Logger logger = LoggerFactory.getLogger(StopDBInstance.class);

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