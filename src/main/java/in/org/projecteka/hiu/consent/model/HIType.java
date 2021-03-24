package in.org.projecteka.hiu.consent.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum HIType {
    //A Superset of all HITypes,
    // the applicable HITypes will be a subset coming from corresponding valuesets.json
    CONDITION("Condition"),
    OBSERVATION("Observation"),
    DIAGNOSTIC_REPORT("DiagnosticReport"),
    MEDICATION_REQUEST("MedicationRequest"),
    DOCUMENT_REFERENCE("DocumentReference"),
    PRESCRIPTION("Prescription"),
    IMMUNIZATION_RECORD("ImmunizationRecord"),
    DISCHARGE_SUMMARY("DischargeSummary"),
    OP_CONSULTATION("OPConsultation"),
    HEALTH_DOCUMENT_RECORD("HealthDocumentRecord"),
    WELLNESS_RECORD("WellnessRecord");

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
