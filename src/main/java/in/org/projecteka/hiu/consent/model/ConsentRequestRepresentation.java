package in.org.projecteka.hiu.consent.model;

import in.org.projecteka.hiu.clients.Patient;
import lombok.SneakyThrows;
import lombok.Value;

import java.text.SimpleDateFormat;
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
        var format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        var withMillSeconds = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        return new ConsentRequestRepresentation(
                consentRequest.getId(),
                new PatientRepresentation(
                        patient.getIdentifier(),
                        patient.getFirstName(),
                        patient.getLastName()),
                consentRequest.getStatus(),
                withMillSeconds.parse(consentRequest.getPermission().getDataExpiryAt()),
                format.parse(consentRequest.getCreatedDate()),
                format.parse(consentRequest.getCreatedDate()));
    }
}

