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

public class StartDBInstance {
    private static final Logger logger = LoggerFactory.getLogger(StartDBInstance.class);

    public static void main(String[] args) throws IOException {
        Properties credentials = new Properties();
        credentials.load(new FileInputStream("app/dbcredentials.properties"));
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