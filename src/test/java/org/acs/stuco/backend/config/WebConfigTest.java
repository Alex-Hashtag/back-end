package org.acs.stuco.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class WebConfigTest {

    @Test
    void addResourceHandlers_ShouldRegisterUploadsDirectory() {
        // Arrange
        WebConfig webConfig = new WebConfig();
        ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
        ResourceHandlerRegistration registration = mock(ResourceHandlerRegistration.class);
        
        when(registry.addResourceHandler("/uploads/**")).thenReturn(registration);
        
        // Act
        webConfig.addResourceHandlers(registry);
        
        // Assert
        verify(registry).addResourceHandler("/uploads/**");
        verify(registration).addResourceLocations(contains("file:"));
    }
    
    @Test
    void webConfig_ShouldWorkWithSpringMVC() {
        // This test verifies that WebConfig can be properly initialized by Spring MVC
        
        // Arrange
        GenericWebApplicationContext context = new GenericWebApplicationContext();
        context.registerBean(WebConfig.class);
        context.refresh();
        
        MockServletContext servletContext = new MockServletContext();
    }
}
