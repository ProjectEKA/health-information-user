package in.org.projecteka.hiu.patient.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class FindPatientRequest {
    private final UUID requestId;
    private final LocalDateTime timestamp;
    private final FindPatientQuery query;
}
