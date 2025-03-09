package it.polito.cloudresources.be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Cloud Resource Management system.
 * This Spring Boot application provides REST APIs for resource booking and management.
 */
@SpringBootApplication
public class ResourceManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResourceManagementApplication.class, args);
    }
}