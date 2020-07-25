package in.org.projecteka.hiu.dataflow.model;

import java.util.Arrays;

public enum HealthInfoStatus {
    RECEIVED,
    PROCESSING,
    SUCCEEDED,
    ERRORED,
    PARTIAL;

    public static HealthInfoStatus fromString(String value){
        return Arrays.stream(HealthInfoStatus.values()).anyMatch(healthInfoStatus -> healthInfoStatus.name().equals(value))
                ? HealthInfoStatus.valueOf(value) : null;
    }
}
