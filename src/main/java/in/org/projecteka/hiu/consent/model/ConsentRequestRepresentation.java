package in.org.projecteka.hiu.consent.model;

import in.org.projecteka.hiu.clients.Patient;
import lombok.SneakyThrows;
import lombok.Value;

import java.util.Date;

@Value
public class ConsentRequestRepresentation {
    private String id;
    private PatientRepresentation patient;
    private ConsentStatus status;
    private Date expiredDate;
    private Date createdDate;
    private Date approvedDate;

    @SneakyThrows
    public static ConsentRequestRepresentation toConsentRequestRepresentation(
            Patient patient,
            in.org.projecteka.hiu.consent.model.ConsentRequest consentRequest) {
        return new ConsentRequestRepresentation(
                consentRequest.getId(),
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

