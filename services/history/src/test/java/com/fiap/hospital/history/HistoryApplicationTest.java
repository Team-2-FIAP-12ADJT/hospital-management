package com.fiap.hospital.history;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class HistoryApplicationTest {

    @Test
    void mainDelegatesToSpringApplication() {
        String[] args = new String[] {"--spring.main.web-application-type=none"};
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            spring.when(() -> SpringApplication.run(eq(HistoryApplication.class), eq(args)))
                    .thenReturn(context);
            HistoryApplication.main(args);
            spring.verify(() -> SpringApplication.run(HistoryApplication.class, args));
        }
    }

    @Test
    void instantiatesApplication() {
        assertNotNull(new HistoryApplication());
    }
}
