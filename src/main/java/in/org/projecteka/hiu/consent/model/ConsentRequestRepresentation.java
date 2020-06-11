package in.org.projecteka.hiu.consent.model;

import in.org.projecteka.hiu.clients.Patient;
import lombok.SneakyThrows;
import lombok.Value;

import java.time.LocalDateTime;

@Value
public class ConsentRequestRepresentation {
    private String id;
    private String consentRequestId;
    private PatientRepresentation patient;
    private ConsentStatus status;
    private LocalDateTime expiredDate;
    private LocalDateTime createdDate;
    private LocalDateTime approvedDate;

    @SneakyThrows
    public static ConsentRequestRepresentation toConsentRequestRepresentation(
            Patient patient,
            in.org.projecteka.hiu.consent.model.ConsentRequest consentRequest, String consentRequestId) {
        return new ConsentRequestRepresentation(
                consentRequest.getId(),
                consentRequestId,
                new PatientRepresentation(
                        patient.getIdentifier(),
                        patient.getFirstName(),
                        patient.getLastName()),
                consentRequest.getStatus(),
                consentRequest.getPermission().getDataEraseAt(),
                consentRequest.getCreatedDate(),
                consentRequest.getCreatedDate());
    }
}

