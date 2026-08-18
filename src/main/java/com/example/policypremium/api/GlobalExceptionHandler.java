package com.example.policypremium.api;

import com.example.policypremium.domain.CoverageType;
import com.example.policypremium.domain.Region;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates failures into RFC 7807 problem details, so every error shares one shape.
 *
 * <p>The distinction that matters here: a request that is well-formed JSON but breaks a rule
 * is a validation failure with per-field messages, while a request that cannot be parsed at
 * all - an unknown enum value, malformed JSON - never reaches validation. Both are the
 * caller's fault and both must be 400, so the second is handled explicitly rather than being
 * allowed to surface as a 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Well-formed request that violates a constraint. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationFailure(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(error -> error.getField()))
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * Body that could not be parsed at all - malformed JSON, or a value outside an enum. Without
     * this the framework would surface it as a 500, blaming the server for a caller error.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body could not be parsed");
        problem.setTitle("Malformed request");
        problem.setProperty("accepted", acceptedValues());
        return problem;
    }

    /** Domain invariant violated by input that passed bean validation. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }

    private static Map<String, List<String>> acceptedValues() {
        Map<String, List<String>> accepted = new LinkedHashMap<>();
        accepted.put("coverageType", names(CoverageType.values()));
        accepted.put("region", names(Region.values()));
        return accepted;
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
