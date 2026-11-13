package com.bart.maris.middleware.security;

import com.bart.maris.middleware.config.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/** Writes RFC 9457 problem details from security filters, where @RestControllerAdvice does not apply. */
final class ProblemWriter {
    private ProblemWriter() {}

    static void write(ObjectMapper om, HttpServletResponse res, HttpStatus status, String code, String detail) throws IOException {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setProperty("code", code);
        p.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        om.writeValue(res.getOutputStream(), p);
    }
}
