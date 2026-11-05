package com.wealthmax.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wealthmax.common.logging.MdcKeys;

/**
 * The real Spring MVC set-up (so a wrong URL, method or content type is raised by the framework itself),
 * the real GlobalExceptionHandler, and a probe controller that throws on demand. No database.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
// Boot's component scan skips classes nested in a test class, so the probe has to be imported by hand
@Import(GlobalExceptionHandlerTest.ProbeController.class)
class GlobalExceptionHandlerTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mockMvc;

    private ListAppender<ILoggingEvent> logs;
    private Logger handlerLogger;

    @BeforeEach
    void setUp() {
        MDC.put(MdcKeys.TRACE_ID, "trace-123");
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logs = new ListAppender<>();
        logs.start();
        handlerLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        handlerLogger.detachAppender(logs);
        MDC.clear();
    }

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        record Body(@NotBlank String name) {
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Job 99 does not exist");
        }

        @GetMapping("/duplicate")
        void duplicate() {
            throw new DuplicateResourceException("An application already exists");
        }

        @GetMapping("/invalid")
        void invalid() {
            throw new InvalidDataException("Salary range is reversed");
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new UnauthorizedException("Invalid email or password");
        }

        @GetMapping("/denied")
        void denied() {
            throw new AccessDeniedException("method security said no for user 42");
        }

        @GetMapping("/not-authenticated")
        void notAuthenticated() {
            throw new BadCredentialsException("bad credentials for someone@example.test");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @GetMapping("/unique")
        void unique() {
            throw integrity("23505", "uk_probe_name");
        }

        @GetMapping("/foreign-key")
        void foreignKey() {
            throw integrity("23503", "fk_probe_parent");
        }

        @GetMapping("/not-null")
        void notNull() {
            throw integrity("23502", "probe_name_not_null");
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody Body body) {
        }

        private static DataIntegrityViolationException integrity(String sqlState, String constraint) {
            SQLException sql = new SQLException("driver message", sqlState);
            return new DataIntegrityViolationException("could not execute statement",
                    new ConstraintViolationException("constraint violated", sql, constraint));
        }
    }

    @Test
    @DisplayName("ResourceNotFoundException is a 404 problem with its code, message and traceId")
    void resourceNotFound_is404() throws Exception {
        mockMvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Job 99 does not exist"))
                .andExpect(jsonPath("$.traceId").value("trace-123"));
    }

    @Test
    @DisplayName("DuplicateResourceException is a 409 problem")
    void duplicate_is409() throws Exception {
        mockMvc.perform(get("/probe/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.detail").value("An application already exists"));
    }

    @Test
    @DisplayName("InvalidDataException is a 400 problem")
    void invalid_is400() throws Exception {
        mockMvc.perform(get("/probe/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                .andExpect(jsonPath("$.detail").value("Salary range is reversed"));
    }

    @Test
    @DisplayName("UnauthorizedException is a 401 problem")
    void unauthorized_is401() throws Exception {
        mockMvc.perform(get("/probe/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("a method-security AccessDeniedException is a 403 with a fixed message, not a 500")
    void accessDenied_is403_andDoesNotLeakTheReason() throws Exception {
        mockMvc.perform(get("/probe/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.detail").value("You do not have permission to do this"));
    }

    @Test
    @DisplayName("a Spring Security AuthenticationException is a 401 with a fixed message")
    void authenticationException_is401_andDoesNotLeakTheReason() throws Exception {
        mockMvc.perform(get("/probe/not-authenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.detail").value("Authentication is required"));
    }

    @Test
    @DisplayName("a failed bean validation is a 400 with one entry per field")
    void validationFailure_is400_withFieldErrors() throws Exception {
        mockMvc.perform(post("/probe/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").value("trace-123"));
    }

    @Test
    @DisplayName("an unexpected exception is a 500 with a fixed message that hides the internal detail")
    void unexpected_is500_andHidesTheDetail() throws Exception {
        String body = mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.traceId").value("trace-123"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("secret internal detail");
    }

    @Test
    @DisplayName("a unique-constraint violation (SQLState 23505) is a 409 and never names the constraint")
    void uniqueViolation_is409_withoutTheConstraintName() throws Exception {
        String body = mockMvc.perform(get("/probe/unique"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("uk_probe_name");
    }

    @Test
    @DisplayName("a foreign-key violation (SQLState 23503) is a 409 data conflict")
    void foreignKeyViolation_is409() throws Exception {
        mockMvc.perform(get("/probe/foreign-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
    }

    @Test
    @DisplayName("a NOT NULL violation (SQLState 23502) is a bug in our code, so it is a 500")
    void notNullViolation_is500() throws Exception {
        mockMvc.perform(get("/probe/not-null"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("an unknown path is a 404 problem with a traceId, not a 500")
    void unknownPath_is404Problem() throws Exception {
        mockMvc.perform(get("/probe/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("trace-123"));
    }

    @Test
    @DisplayName("the wrong HTTP method is a 405 problem, not a 500")
    void wrongMethod_is405Problem() throws Exception {
        mockMvc.perform(post("/probe/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.traceId").value("trace-123"));
    }

    @Test
    @DisplayName("an unsupported content type is a 415 problem, not a 500")
    void wrongContentType_is415Problem() throws Exception {
        mockMvc.perform(post("/probe/validated").contentType(MediaType.TEXT_PLAIN).content("name"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("an unreadable JSON body is a 400 problem")
    void malformedJson_is400Problem() throws Exception {
        mockMvc.perform(post("/probe/validated").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("a 4xx is logged at WARN without a stack trace")
    void clientError_isLoggedAtWarnWithoutStack() throws Exception {
        mockMvc.perform(get("/probe/not-found"));

        List<ILoggingEvent> events = logs.list;
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(events.get(0).getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("a 5xx is logged at ERROR with the stack trace")
    void serverError_isLoggedAtErrorWithStack() throws Exception {
        mockMvc.perform(get("/probe/boom"));

        List<ILoggingEvent> events = logs.list;
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getLevel()).isEqualTo(Level.ERROR);
        assertThat(events.get(0).getThrowableProxy()).isNotNull();
    }
}
