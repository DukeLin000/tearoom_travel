package com.tripmate.shared;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class Errors {
    public record ErrorBody(String code, String message, String traceId) {}
    public static ErrorBody body(String code, String message, HttpServletRequest request) {
        Object trace = request.getAttribute("traceId");
        return new ErrorBody(code, message, trace == null ? UUID.randomUUID().toString() : trace.toString());
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> api(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(body(exception.code(), exception.getMessage(), request));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
        HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        HandlerMethodValidationException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ErrorBody> invalid(Exception exception, HttpServletRequest request) {
        String message = "Invalid request fields, types or format";
        if (exception instanceof MethodArgumentNotValidException validation && validation.getBindingResult().getFieldError() != null) {
            var error = validation.getBindingResult().getFieldError();
            message = error.getField() + ": " + error.getDefaultMessage();
        }
        return ResponseEntity.badRequest().body(body("INVALID_INPUT", message, request));
    }
    @ExceptionHandler(DataAccessResourceFailureException.class)
    ResponseEntity<ErrorBody> unavailable(Exception exception, HttpServletRequest request) {
        LoggerFactory.getLogger(Errors.class).error("Database unavailable traceId={}", request.getAttribute("traceId"), exception);
        return ResponseEntity.status(503).body(body("DATABASE_UNAVAILABLE", "Database temporarily unavailable", request));
    }
    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ErrorBody> routing(Exception exception, HttpServletRequest request) {
        var status = ((ErrorResponse) exception).getStatusCode();
        String code = status.value() == 404 ? "NOT_FOUND" : status.value() == 405 ? "METHOD_NOT_ALLOWED" : "UNSUPPORTED_MEDIA_TYPE";
        return ResponseEntity.status(status).body(body(code, "Invalid endpoint, method or content type", request));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception exception, HttpServletRequest request) {
        LoggerFactory.getLogger(Errors.class).error("Request failed traceId={}", request.getAttribute("traceId"), exception);
        return ResponseEntity.internalServerError().body(body("INTERNAL_ERROR", "Unexpected server error", request));
    }
}
