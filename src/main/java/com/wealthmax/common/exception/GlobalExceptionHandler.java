package com.wealthmax.common.exception;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.wealthmax.common.logging.MdcKeys;

/**
 * The one place that turns an exception into a response (ADR-011). Every body is an RFC 9457 ProblemDetail
 * with a stable "code" and the request's "traceId". Controllers never catch exceptions.
 *
 * Log lines carry the code and the exception class, not the message, because a message can hold an id or a
 * value from the request and logs must stay free of personal data.
 *
 * SQLState 23505 (unique) and 23503 (foreign key) are the client's conflict; any other database error, such
 * as 23502 (NOT NULL), means our code sent bad data, so it is a 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final int MAX_CAUSE_DEPTH = 10;

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), ex);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ProblemDetail> handleDuplicate(DuplicateResourceException ex) {
        return respond(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", ex.getMessage(), ex);
    }

    @ExceptionHandler(InvalidDataException.class)
    public ResponseEntity<ProblemDetail> handleInvalid(InvalidDataException ex) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_DATA", ex.getMessage(), ex);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorized(UnauthorizedException ex) {
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), ex);
    }

    /** Method security (@PreAuthorize) throws this inside the controller layer, where only this advice sees it. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to do this", ex);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required", ex);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        ConstraintViolationException violation = findConstraintViolation(ex);
        String sqlState = violation == null ? null : violation.getSQLState();

        if (UNIQUE_VIOLATION.equals(sqlState)) {
            return respond(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE",
                    "A record with the same value already exists", ex, violation.getConstraintName());
        }
        if (FOREIGN_KEY_VIOLATION.equals(sqlState)) {
            return respond(HttpStatus.CONFLICT, "DATA_CONFLICT",
                    "The request conflicts with existing data", ex, violation.getConstraintName());
        }
        return handleUnexpected(ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", ex);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.add(Map.of("field", error.getField(), "message", messageOf(error.getDefaultMessage()))));
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                errors.add(Map.of("field", error.getObjectName(), "message", messageOf(error.getDefaultMessage()))));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Every error the framework raises itself (404, 405, 413, 415, unreadable body...) gets a code and a traceId. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        // For 404, 405, 415... Spring passes body == null and only builds the ProblemDetail afterwards,
        // so build it here first (the same call the base class makes) or there would be nothing to enrich.
        if (body == null && ex instanceof ErrorResponse errorResponse) {
            body = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
        }
        if (body instanceof ProblemDetail problem) {
            if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
                problem.setProperty("code", codeFor(statusCode));
            }
            addTraceId(problem);
            logRejected(statusCode, String.valueOf(problem.getProperties().get("code")), ex, null);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private ResponseEntity<ProblemDetail> respond(HttpStatus status, String code, String detail, Exception ex) {
        return respond(status, code, detail, ex, null);
    }

    private ResponseEntity<ProblemDetail> respond(HttpStatus status, String code, String detail, Exception ex,
            String constraintName) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        addTraceId(problem);
        logRejected(status, code, ex, constraintName);
        return ResponseEntity.status(status).body(problem);
    }

    private void logRejected(HttpStatusCode status, String code, Exception ex, String constraintName) {
        if (status.is5xxServerError()) {
            log.error("Request failed: {} ({})", code, ex.getClass().getSimpleName(), ex);
        } else if (constraintName != null) {
            log.warn("Request rejected: {} ({}, constraint {})", code, ex.getClass().getSimpleName(), constraintName);
        } else {
            log.warn("Request rejected: {} ({})", code, ex.getClass().getSimpleName());
        }
    }

    private static void addTraceId(ProblemDetail problem) {
        String traceId = MDC.get(MdcKeys.TRACE_ID);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
    }

    private static String codeFor(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.name() : "ERROR";
    }

    private static String messageOf(String message) {
        return message != null ? message : "is invalid";
    }

    private static ConstraintViolationException findConstraintViolation(Throwable start) {
        Throwable current = start;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof ConstraintViolationException violation) {
                return violation;
            }
            current = current.getCause();
        }
        return null;
    }
}
