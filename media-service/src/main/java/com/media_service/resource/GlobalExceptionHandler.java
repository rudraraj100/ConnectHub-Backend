package com.media_service.resource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Converts all unhandled exceptions in the media-service to JSON error responses.
 *
 * Without this, Spring returns an HTML error page on 400/500, which confuses
 * the gateway's RestTemplate and produces opaque errors in Angular.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParam(
            MissingServletRequestParameterException ex) {
        log.warn("[Media] Missing required request parameter: {}", ex.getParameterName());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Missing required parameter: " + ex.getParameterName()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAll(Exception ex) {
        log.error("[Media] Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(Map.of(
                        "error", ex.getMessage() != null ? ex.getMessage() : "Internal server error"
                ));
    }
}
