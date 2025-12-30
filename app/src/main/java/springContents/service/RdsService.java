package springContents.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesRequest;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesResponse;
import software.amazon.awssdk.services.rds.model.RdsException;
import software.amazon.awssdk.services.rds.model.StartDbInstanceRequest;
import software.amazon.awssdk.services.rds.model.StopDbInstanceRequest;

import java.io.IOException;
import java.util.Properties;

@Service
public class RdsService {
    private static final Logger logger = LoggerFactory.getLogger(RdsService.class);
    private final RdsClient rdsClient;
    private final String dbInstanceIdentifier;
    private final Region region;

    public RdsService(ResourceLoader resourceLoader) throws IOException {
        Properties credentials = new Properties();
        Resource resource = resourceLoader.getResource("classpath:dbcredentials.properties");
        credentials.load(resource.getInputStream());

        this.dbInstanceIdentifier = credentials.getProperty("instance");
        String regionStr = credentials.getProperty("rds.region", "us-east-1");
        this.region = Region.of(regionStr);

        this.rdsClient = RdsClient.builder()
                .region(region)
                .build();

        logger.info("RdsService initialized for instance: {} in region: {}", 
                    dbInstanceIdentifier, region);
    }

    /**
     * Get the current status of the RDS database instance
     */
    public String getDatabaseStatus() {
        try {
            DescribeDbInstancesRequest request = DescribeDbInstancesRequest.builder()
                    .dbInstanceIdentifier(dbInstanceIdentifier)
                    .build();

            DescribeDbInstancesResponse response = rdsClient.describeDBInstances(request);
            
            if (response.dbInstances().isEmpty()) {
                return "not-found";
            }

            return response.dbInstances().get(0).dbInstanceStatus();
        } catch (RdsException e) {
            logger.error("Error getting database status: {}", e.getMessage());
            return "error";
        }
    }

    /**
     * Start the RDS database instance
     */
    public void startDatabase() {
        try {
            StartDbInstanceRequest request = StartDbInstanceRequest.builder()
                    .dbInstanceIdentifier(dbInstanceIdentifier)
                    .build();

            rdsClient.startDBInstance(request);
            logger.info("Successfully initiated start for RDS instance: {}", dbInstanceIdentifier);
        } catch (RdsException e) {
            logger.error("Error starting database: {}", e.getMessage());
            throw new RuntimeException("Failed to start database: " + e.getMessage(), e);
        }
    }

    /**
     * Stop the RDS database instance
     */
    public void stopDatabase() {
        try {
            StopDbInstanceRequest request = StopDbInstanceRequest.builder()
                    .dbInstanceIdentifier(dbInstanceIdentifier)
                    .build();

            rdsClient.stopDBInstance(request);
            logger.info("Successfully initiated stop for RDS instance: {}", dbInstanceIdentifier);
        } catch (RdsException e) {
            logger.error("Error stopping database: {}", e.getMessage());
            throw new RuntimeException("Failed to stop database: " + e.getMessage(), e);
        }
    }
}