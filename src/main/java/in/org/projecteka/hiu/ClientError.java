package in.org.projecteka.hiu;

import lombok.Getter;
import lombok.ToString;
import org.springframework.http.HttpStatus;

import static in.org.projecteka.hiu.ErrorCode.CONSENT_ARTEFACT_NOT_FOUND;
import static in.org.projecteka.hiu.ErrorCode.CONSENT_REQUEST_NOT_FOUND;
import static in.org.projecteka.hiu.ErrorCode.FAILED_TO_NOTIFY_CM;
import static in.org.projecteka.hiu.ErrorCode.INVALID_TOKEN;
import static in.org.projecteka.hiu.ErrorCode.NETWORK_SERVICE_ERROR;
import static in.org.projecteka.hiu.ErrorCode.NO_RESULT_FROM_GATEWAY;
import static in.org.projecteka.hiu.ErrorCode.QUEUE_NOT_FOUND;
import static in.org.projecteka.hiu.ErrorCode.UNAUTHORIZED_REQUESTER;
import static in.org.projecteka.hiu.ErrorCode.UNKNOWN_ERROR;
import static in.org.projecteka.hiu.ErrorCode.VALIDATION_FAILED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Getter
@ToString
public class ClientError extends Throwable {

    private static final String CANNOT_PROCESS_REQUEST_TRY_LATER = "Cannot process the request at the moment," +
            "please try later.";
    private final HttpStatus httpStatus;
    private final ErrorRepresentation error;

    public ClientError(HttpStatus httpStatus, ErrorRepresentation errorRepresentation) {
        this.httpStatus = httpStatus;
        error = errorRepresentation;
    }

    public static ClientError consentRequestNotFound() {
        return new ClientError(NOT_FOUND,
                new ErrorRepresentation(new Error(CONSENT_REQUEST_NOT_FOUND, "Cannot find the consent request")));
    }

    public static ClientError consentArtefactNotFound() {
        return new ClientError(INTERNAL_SERVER_ERROR,
                new ErrorRepresentation(new Error(CONSENT_ARTEFACT_NOT_FOUND, "Cannot find the consent artefact")));
    }

    public static ClientError consentArtefactGone() {
        return new ClientError(INTERNAL_SERVER_ERROR,
                new ErrorRepresentation(new Error(CONSENT_ARTEFACT_NOT_FOUND, "Consent artefact expired")));
    }

    public static ClientError dbOperationFailure(String message) {
        return new ClientError(INTERNAL_SERVER_ERROR, new ErrorRepresentation(new Error(UNKNOWN_ERROR, message)));
    }

    public static ClientError queueNotFound() {
        return new ClientError(INTERNAL_SERVER_ERROR,
                new ErrorRepresentation(new Error(QUEUE_NOT_FOUND, "Queue not found")));
    }

    public static ClientError unauthorizedRequester() {
        return new ClientError(UNAUTHORIZED,
                new ErrorRepresentation(new Error(UNAUTHORIZED_REQUESTER,
                        "Requester is not authorized to perform this action")));
    }

    public static ClientError invalidHealthInformationRequest() {
        return new ClientError(BAD_REQUEST,
                new ErrorRepresentation(new Error(ErrorCode.INVALID_REQUEST,
                        "Action can't be performed, consent request is not granted yet")));
    }

    public static ClientError invalidEntryError(String errorMessage) {
        return new ClientError(BAD_REQUEST,
                new ErrorRepresentation(new Error(ErrorCode.INVALID_DATA_FLOW_ENTRY, errorMessage)));
    }

    public static ClientError authenticationFailed() {
        return new ClientError(INTERNAL_SERVER_ERROR,
                new ErrorRepresentation(new Error(UNKNOWN_ERROR, "Something went wrong")));
    }

    public static ClientError validationFailed() {
        return new ClientError(INTERNAL_SERVER_ERROR,
                new ErrorRepresentation(new Error(VALIDATION_FAILED, "Validation Failed")));
    }

    public static ClientError failedToNotifyCM() {
        return new ClientError(INTERNAL_SERVER_ERROR,
                new ErrorRepresentation(new Error(FAILED_TO_NOTIFY_CM, "Failed to notify consent manager")));
    }

    public static ClientError invalidDataFromGateway() {
        return new ClientError(BAD_REQUEST,
                new ErrorRepresentation(new Error(ErrorCode.INVALID_DATA_FROM_GATEWAY,
                        "Invalid Data from Gateway. Must have either a payload or error")));
    }

    public static ClientError gatewayTimeOut() {
        return new ClientError(GATEWAY_TIMEOUT,
                new ErrorRepresentation(new Error(NO_RESULT_FROM_GATEWAY, "Could not connect to Gateway")));
    }

    public static ClientError consentRequestAlreadyUpdated() {
        return new ClientError(INTERNAL_SERVER_ERROR,
                new ErrorRepresentation(new Error(VALIDATION_FAILED, "Consent request is already updated.")));
    }

    public static ClientError unknownError() {
        return new ClientError(INTERNAL_SERVER_ERROR, new ErrorRepresentation(new Error(UNKNOWN_ERROR,
                "Unknown error")));
    }

    public static ClientError patientNotFound() {
        return new ClientError(NOT_FOUND,
                new ErrorRepresentation(new Error(ErrorCode.PATIENT_NOT_FOUND, "Patient not found")));
    }

    public static ClientError unAuthorized() {
        return new ClientError(UNAUTHORIZED,
                new ErrorRepresentation(new Error(INVALID_TOKEN, "Token verification failed")));
    }

    public static ClientError networkServiceCallFailed() {
        return new ClientError(INTERNAL_SERVER_ERROR,
                new ErrorRepresentation(new Error(NETWORK_SERVICE_ERROR, CANNOT_PROCESS_REQUEST_TRY_LATER)));
    }
}
