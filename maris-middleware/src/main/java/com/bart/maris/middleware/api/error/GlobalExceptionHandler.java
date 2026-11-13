package com.bart.maris.middleware.api.error;

import com.bart.maris.middleware.config.RequestIdFilter;
import com.bart.maris.middleware.domain.LegacyBusinessException;
import com.bart.maris.middleware.domain.screens.ActionNotMigratedException;
import com.bart.maris.middleware.legacy.UnknownProgramException;
import com.bart.maris.middleware.legacy.client.LegacyProgramFailedException;
import com.bart.maris.middleware.legacy.client.LegacyProtocolException;
import com.bart.maris.middleware.legacy.client.LegacyUnavailableException;
import com.bart.maris.middleware.legacy.codec.CodecException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves as an RFC 9457 problem detail with a stable {@code code} and the {@code requestId}.
 * Backend details (HTML error pages, stack traces) are logged, never sent to the browser.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CodecException.class)
    ProblemDetail invalidInput(CodecException e) {
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "INVALID_INPUT", e.getMessage());
        if (e.field() != null) p.setProperty("field", e.field());
        return p;
    }

    @ExceptionHandler(com.bart.maris.middleware.domain.InvalidRequestException.class)
    ProblemDetail invalidRequest(com.bart.maris.middleware.domain.InvalidRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_INPUT", e.getMessage());
    }

    @ExceptionHandler(UnknownProgramException.class)
    ProblemDetail unknownProgram(UnknownProgramException e) {
        return problem(HttpStatus.NOT_FOUND, "UNKNOWN_PROGRAM", e.getMessage());
    }

    @ExceptionHandler(LegacyUnavailableException.class)
    ProblemDetail unavailable(LegacyUnavailableException e) {
        log.error("MARIS backend unavailable: {}", e.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "BACKEND_UNAVAILABLE",
                "The MARIS system is not responding. Try again in a few minutes.");
    }

    @ExceptionHandler(LegacyProtocolException.class)
    ProblemDetail protocol(LegacyProtocolException e) {
        log.error("MARIS backend protocol error: {}", e.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "BACKEND_ERROR", "The MARIS system returned an error. Quote the request ID when reporting it.");
    }

    @ExceptionHandler(LegacyProgramFailedException.class)
    ProblemDetail programFailed(LegacyProgramFailedException e) {
        log.error("{}", e.getMessage());
        ProblemDetail p = problem(HttpStatus.BAD_GATEWAY, "BACKEND_PROGRAM_FAILED",
                "The MARIS system could not complete the request. Quote the request ID when reporting it.");
        p.setProperty("program", e.program());
        p.setProperty("errorCode", e.errorCode());
        return p;
    }

    @ExceptionHandler(LegacyBusinessException.class)
    ProblemDetail business(LegacyBusinessException e) {
        ProblemDetail p = problem(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_ERROR", e.getMessage());
        p.setProperty("program", e.program());
        p.setProperty("returnCode", e.control().returnCode());
        if (e.message() != null) p.setProperty("messageCode", e.message().code());
        return p;
    }

    @ExceptionHandler(ActionNotMigratedException.class)
    ProblemDetail notMigrated(ActionNotMigratedException e) {
        return problem(HttpStatus.NOT_IMPLEMENTED, "NOT_MIGRATED", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unexpected error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong. Quote the request ID when reporting it.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields are missing or invalid.");
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        p.setProperty("fields", fields);
        return ResponseEntity.badRequest().body(p);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers, HttpStatusCode status,
                                                          WebRequest request) {
        if (body instanceof ProblemDetail p) p.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return super.createResponseEntity(body, headers, status, request);
    }

    static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setProperty("code", code);
        p.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return p;
    }
}
