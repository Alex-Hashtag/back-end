package org.acs.stuco.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;


@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "StuCo API Documentation",
                version = "1.0",
                description = "API Documentation"
        )
)
public class OpenApiConfig
{
}

