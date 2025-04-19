package org.acs.stuco.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class AsyncConfigTest {

    @Autowired
    private AsyncConfig asyncConfig;

    @Test
    void getAsyncExecutor_ShouldReturnThreadPoolTaskExecutor() {
        // Act
        Executor executor = asyncConfig.getAsyncExecutor();
        
        // Assert
        assertNotNull(executor);
        assertTrue(executor instanceof ThreadPoolTaskExecutor);
    }
}
