package in.org.projecteka.hiu;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ClientError extends Throwable {

    private static final String CANNOT_PROCESS_REQUEST_TRY_LATER = "Cannot process the request at the moment, please " +
            "try later.";
    private final HttpStatus httpStatus;
    private final ErrorRepresentation error;

    public ClientError(HttpStatus httpStatus, ErrorRepresentation errorRepresentation) {
        this.httpStatus = httpStatus;
        error = errorRepresentation;
    }

    public static ClientError consentRequestNotFound() {
        return new ClientError(
                HttpStatus.NOT_FOUND,
                new ErrorRepresentation(new Error(
                        ErrorCode.CONSENT_REQUEST_NOT_FOUND,
                        "Cannot find the consent request")));
    }

    public static ClientError consentArtefactNotFound() {
        return new ClientError(
                HttpStatus.NOT_FOUND,
                new ErrorRepresentation(new Error(
                        ErrorCode.CONSENT_ARTEFACT_NOT_FOUND,
                        "Cannot find the consent artefact")));
    }

    public static ClientError dbOperationFailure(String message) {
        return new ClientError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                new ErrorRepresentation(new Error(
                        ErrorCode.UNKNOWN_ERROR,
                        message)));
    }

    public static ClientError queueNotFound() {
        return new ClientError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                new ErrorRepresentation(new Error(
                        ErrorCode.QUEUE_NOT_FOUND,
                        "Queue not found")));
    }

    public static ClientError unauthorizedRequester() {
        return new ClientError(
                HttpStatus.UNAUTHORIZED,
                new ErrorRepresentation(new Error(
                        ErrorCode.UNAUTHORIZED_REQUESTER,
                        "Requester is not authorized to perform this action")));
    }

    public static ClientError unauthorized() {
        return new ClientError(
                HttpStatus.UNAUTHORIZED,
                new ErrorRepresentation(new Error(
                        ErrorCode.UNAUTHORIZED,
                        "Action can't be performed, unauthorized")));
    }

    public static ClientError invalidEntryError(String errorMessage) {
        return new ClientError(
                HttpStatus.BAD_REQUEST,
                new ErrorRepresentation(new Error(ErrorCode.INVALID_DATA_FLOW_ENTRY,
                        errorMessage)));
    }

    public static ClientError authenticationFailed() {
        return new ClientError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                new ErrorRepresentation(new Error(
                        ErrorCode.UNKNOWN_ERROR,
                        "Something went wrong")));
    }
}
