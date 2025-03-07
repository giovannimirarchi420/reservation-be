package it.polito.cloudresources.be;

import org.modelmapper.ModelMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Main application class for the Cloud Resource Management system.
 * This Spring Boot application provides REST APIs for resource booking and management.
 */
@SpringBootApplication
@EnableJpaAuditing
public class ResourceManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResourceManagementApplication.class, args);
    }
    
    /**
     * Creates a ModelMapper bean for DTO-Entity conversions
     * @return ModelMapper instance
     */
    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }
}
