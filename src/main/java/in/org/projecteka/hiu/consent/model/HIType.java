package in.org.projecteka.hiu.consent.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum HIType {

    CONDITION("Condition"),
    OBSERVATION("Observation"),
    DIAGNOSTICREPORT("DiagnosticReport"),
    MEDICATIONREQUEST("MedicationRequest");

    private final String resourceType;
    HIType(String value) {
        resourceType = value;
    }

    @JsonValue
    public String getValue() {
        return resourceType;
    }

    @JsonCreator
    public HIType findByValue(String input) {
        return Arrays.stream(HIType.values())
                .filter(hiType -> hiType.resourceType.equals(input))
                .findAny().get();
    }
}
