package org.acs.stuco.backend.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;


@Configuration
public class RestClientConfig
{

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder)
    {
        return builder
                .requestFactory(() ->
                {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(60000);
                    factory.setReadTimeout(60000);
                    return factory;
                })
                .build();
    }
}
