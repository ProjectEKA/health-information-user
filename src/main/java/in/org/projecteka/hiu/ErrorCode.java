package in.org.projecteka.hiu;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum ErrorCode {
    UNKNOWN_ERROR(4500),
    CONSENT_REQUEST_NOT_FOUND(4404),
    QUEUE_NOT_FOUND(4550),
    UNAUTHORIZED_REQUESTER(4401),
    INVALID_DATA_FLOW_ENTRY(4406),
    INVALID_USERNAME_OR_PASSWORD(4450),
    INVALID_REQUEST(4400),
    CONSENT_ARTEFACT_NOT_FOUND(4551),
    VALIDATION_FAILED(4552),
    FAILED_TO_NOTIFY_CM(4553),
    INVALID_PURPOSE_OF_USE(4420),
    INVALID_DATA_FROM_GATEWAY(4422),
    NO_RESULT_FROM_GATEWAY(4504),
    PATIENT_NOT_FOUND(4407),
    SERVICE_DOWN(4222);

    private final int value;

    ErrorCode(int val) {
        value = val;
    }

    // Adding @JsonValue annotation that tells the 'value' to be of integer type while de-serializing.
    @JsonValue
    public int getValue() {
        return value;
    }

    @JsonCreator
    public static ErrorCode getNameByValue(int value) {
        return Arrays.stream(ErrorCode.values())
                .filter(errorCode -> errorCode.value == value)
                .findAny()
                .orElse(ErrorCode.UNKNOWN_ERROR);
    }
}