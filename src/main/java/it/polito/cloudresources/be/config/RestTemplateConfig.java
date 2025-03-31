package it.polito.cloudresources.be.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for RestTemplate used in the application
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Creates a RestTemplate bean with custom configuration
     * 
     * @return the configured RestTemplate
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5 seconds
        factory.setReadTimeout(10000);   // 10 seconds
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        return restTemplate;
    }
}