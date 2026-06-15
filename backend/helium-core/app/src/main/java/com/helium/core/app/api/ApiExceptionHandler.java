package com.helium.core.app.api;

import com.helium.core.admin.domain.AdminValidationException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.helium.core.app.api")
public class ApiExceptionHandler {
    @ExceptionHandler(ApiUnauthorizedException.class)
    ResponseEntity<ProblemDetail> unauthorized(ApiUnauthorizedException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication required", exception.getMessage());
    }

    @ExceptionHandler(ApiForbiddenException.class)
    ResponseEntity<ProblemDetail> forbidden(ApiForbiddenException exception) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage());
    }

    @ExceptionHandler(AdminValidationException.class)
    ResponseEntity<ProblemDetail> adminValidation(AdminValidationException exception) {
        if (exception.getMessage() != null && exception.getMessage().contains("authenticated")) {
            return problem(HttpStatus.UNAUTHORIZED, "Authentication required", exception.getMessage());
        }
        return problem(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    ResponseEntity<ProblemDetail> badRequest(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(ApiExceptionHandler::fieldMessage)
            .orElse("request validation failed");
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", detail);
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ProblemDetail> runtime(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Request rejected", exception.getMessage());
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://api.helium.exchange/problems/" + status.value()));
        return ResponseEntity.status(status).body(problem);
    }

    private static String fieldMessage(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
