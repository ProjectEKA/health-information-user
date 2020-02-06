package in.org.projecteka.hiu;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ClientError extends Throwable {

    private static final String CANNOT_PROCESS_REQUEST_TRY_LATER = "Cannot process the request at the moment, please try later.";
    private final HttpStatus httpStatus;
    private final ErrorRepresentation error;

    public ClientError(HttpStatus httpStatus, ErrorRepresentation errorRepresentation) {
        this.httpStatus = httpStatus;
        error = errorRepresentation;
    }

    public static ClientError invalidConsentManager() {
        return new ClientError(
                HttpStatus.UNAUTHORIZED,
                new ErrorRepresentation(new Error(
                        ErrorCode.INVALID_CONSENT_MANAGER,
                        "Cannot find the consent request")));
    }

    public static ClientError consentRequestNotFound() {
        return new ClientError(
                HttpStatus.NOT_FOUND,
                new ErrorRepresentation(new Error(
                        ErrorCode.CONSENT_REQUEST_NOT_FOUND,
                        "Cannot find the consent request")));
    }

    public static ClientError dbOperationFailure(String message) {
        return new ClientError(
                HttpStatus.NOT_FOUND,
                new ErrorRepresentation(new Error(
                        ErrorCode.CONSENT_REQUEST_NOT_FOUND,
                        message)));
    }

    public static ClientError queueNotFound() {
        return new ClientError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                new ErrorRepresentation(new Error(
                        ErrorCode.QUEUE_NOT_FOUND,
                        "Queue not found")));
    }
}
