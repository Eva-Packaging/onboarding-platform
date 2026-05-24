package xyz.catuns.onboarding.service.api.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.UUID;

@Getter
@Setter
public class ErrorResponse extends ProblemDetail {

    private String code;
    private UUID correlationId;

    protected ErrorResponse() {}

    public static ErrorResponse of(HttpStatus status) {
        ErrorResponse error = new ErrorResponse();
        error.setStatus(status.value());
        return error;
    }
}