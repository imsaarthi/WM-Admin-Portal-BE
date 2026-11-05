package com.wealthmax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationContext;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.wealthmax.support.LocalDatabaseOnlyInitializer;

/**
 * Starts the whole application against the local test database: Flyway, auditing, the filters, the error advice,
 * springdoc and actuator on Spring Boot 4.1.2. Until now only slices had started.
 *
 * Real security arrives in Phase 2a. Boot's default would answer 401 to everything, so this test brings a
 * permit-all chain of its own. That is what lets it prove the unknown-path behaviour in the Phase 1 exit criteria.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseOnlyInitializer.class)
@Tag("database")
class WmAdminPortalApplicationTests {

    @TestConfiguration
    static class PermitAllSecurity {

        @Bean
        SecurityFilterChain permitAll(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable())
                    .httpBasic(Customizer.withDefaults())
                    .build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("the whole application starts")
    void contextLoads() {
        assertThat(context.getStartupDate()).isPositive();
    }

    @Test
    @DisplayName("an unknown path is a ProblemDetail 404 with a traceId, not a 500 (Phase 1 exit criterion)")
    void unknownPath_isAProblemDetail404WithATraceId() throws Exception {
        MvcResult result = mockMvc.perform(get("/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn();

        String traceId = result.getResponse().getHeader("X-Trace-Id");
        assertThat(traceId).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).contains(traceId);
    }

    @Test
    @DisplayName("a well-formed X-Request-Id comes back as the traceId in the header and in the error body")
    void incomingRequestId_isTheTraceId() throws Exception {
        mockMvc.perform(get("/no-such-path").header("X-Request-Id", "abc-12345678"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.traceId").value("abc-12345678"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Trace-Id", "abc-12345678"));
    }
}
