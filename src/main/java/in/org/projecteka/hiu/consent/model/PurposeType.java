package in.org.projecteka.hiu.consent.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum PurposeType {
    REFERRAL_SERVICES("ReferralService"),
    EPISODE_OF_CARE("EpisodeOfCare"),
    ENCOUNTER("Encounter"),
    REMOTE_CONSULTING("RemoteConsulting");

    private final String value;
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
