package in.org.projecteka.hiu.consent.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum PurposeType {
    REFERRAL_SERVICES("Referral Service"),
    EPISODE_OF_CARE("Episode of care"),
    ENCOUNTER("Encounter"),
    REMOTE_CONSULTING("Remote Consulting");

    private String value;
    PurposeType(String val) {
        value = val;
    }

    // Adding @JsonValue annotation that tells the 'value' to be of integer type while de-serializing.
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PurposeType getNameByValue(String value) {
        return Arrays.stream(PurposeType.values())
                .filter(purposeType -> purposeType.value.equals(value))
                .findAny().get();
    }
}
