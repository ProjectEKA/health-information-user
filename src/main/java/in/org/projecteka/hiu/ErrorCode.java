package in.org.projecteka.hiu;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum ErrorCode {
    INVALID_CONSENT_MANAGER(1001),
    UNKNOWN_ERROR(1002),
    CONSENT_REQUEST_NOT_FOUND(1003),
    QUEUE_NOT_FOUND(1004),
    UNAUTHORIZED_REQUESTER(1005),
    INVALID_DATA_FLOW_ENTRY(5001),
    INVALID_USERNAME_OR_PASSWORD(1006),
    INVALID_REQUEST(1007),
    UNAUTHORIZED(1008),
    CONSENT_ARTEFACT_NOT_FOUND(1009),
    VALIDATION_FAILED(1010),
    FAILED_TO_NOTIFY_CM(1011),
    INVALID_PURPOSE_OF_USE(1012),
    INVALID_DATA_FROM_GATEWAY(1013),
    NO_RESULT_FROM_GATEWAY(1014);

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