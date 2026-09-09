package tn.civiccare.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import tn.civiccare.observability.Telemetry.ValidationException;

import java.net.URI;
import java.util.Map;

/**
 * Erreurs API au format RFC 9457 Problem Details : code stable, erreurs par champ,
 * statut correct. Jamais de stack trace, de page HTML ni de donnée privée.
 */
@RestControllerAdvice(basePackages = "tn.civiccare.api")
public class ApiExceptionHandler {

    /** Codes de validation métier -> statut + champ concerné (contrat stable pour la SPA). */
    private static FieldError mapValidation(String code) {
        if (code == null) {
            return new FieldError(HttpStatus.UNPROCESSABLE_ENTITY, null, "invalid");
        }
        String[] parts = code.split(":", 2);
        String key = parts[0];
        String field = parts.length > 1 ? parts[1] : null;
        return switch (key) {
            case "position.outside" -> new FieldError(HttpStatus.UNPROCESSABLE_ENTITY, "position", key);
            case "description.invalid" -> new FieldError(HttpStatus.UNPROCESSABLE_ENTITY, "description", key);
            case "email.invalid" -> new FieldError(HttpStatus.UNPROCESSABLE_ENTITY, "email", key);
            case "phone.invalid" -> new FieldError(HttpStatus.UNPROCESSABLE_ENTITY, "phone", key);
            case "consent.required" -> new FieldError(HttpStatus.UNPROCESSABLE_ENTITY, "consent", key);
            case "type.invalid" -> new FieldError(HttpStatus.UNPROCESSABLE_ENTITY, "serviceTypeId", key);
            case "field.required", "field.invalidOption", "field.tooLong", "field.unknown" ->
                    new FieldError(HttpStatus.UNPROCESSABLE_ENTITY, field, key);
            case "media.tooMany", "media.tooLarge", "media.format", "media.dimensions", "media.unreadable" ->
                    new FieldError(HttpStatus.UNPROCESSABLE_ENTITY, "photos", key);
            case "contact.fields" -> new FieldError(HttpStatus.UNPROCESSABLE_ENTITY, null, key);
            case "rate.limited" -> new FieldError(HttpStatus.TOO_MANY_REQUESTS, null, key);
            case "reopen.reasonRequired" -> new FieldError(HttpStatus.UNPROCESSABLE_ENTITY, "reason", key);
            case "transition.invalid" -> new FieldError(HttpStatus.CONFLICT, "status", key);
            case "duplicate.self", "duplicate.chain", "canonical.unknown" ->
                    new FieldError(HttpStatus.UNPROCESSABLE_ENTITY, "canonicalReference", key);
            case "report.unknown", "page.unknown", "department.unknown", "assignee.unknown", "user.unknown" ->
                    new FieldError(HttpStatus.NOT_FOUND, null, key);
            case "user.exists", "user.invalid", "assignee.notInDepartment", "version.stale" ->
                    new FieldError(key.equals("version.stale") ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY,
                            null, key);
            default -> new FieldError(HttpStatus.UNPROCESSABLE_ENTITY, null, key);
        };
    }

    private record FieldError(HttpStatus status, String field, String code) {
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail onValidation(ValidationException e) {
        FieldError mapped = mapValidation(e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(mapped.status());
        problem.setType(URI.create("https://civiccare.tn/problems/" + mapped.code()));
        problem.setTitle("Validation failed");
        problem.setProperty("code", mapped.code());
        if (mapped.field() != null) {
            problem.setProperty("errors", Map.of(mapped.field(), mapped.code()));
        }
        return problem;
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ProblemDetail onAccessDenied(Exception e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setType(URI.create("https://civiccare.tn/problems/forbidden"));
        problem.setTitle("Forbidden");
        problem.setProperty("code", "forbidden");
        return problem;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail onOptimisticLock(ObjectOptimisticLockingFailureException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create("https://civiccare.tn/problems/version.conflict"));
        problem.setTitle("Concurrent modification");
        problem.setProperty("code", "version.conflict");
        return problem;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail onUploadTooLarge(MaxUploadSizeExceededException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        problem.setType(URI.create("https://civiccare.tn/problems/media.tooLarge"));
        problem.setProperty("code", "media.tooLarge");
        return problem;
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ProblemDetail onMissingParam(org.springframework.web.bind.MissingServletRequestParameterException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setProperty("code", "param.missing");
        problem.setProperty("errors", Map.of(e.getParameterName(), "param.missing"));
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onIllegalArgument(IllegalArgumentException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setProperty("code", "request.invalid");
        return problem;
    }
}
