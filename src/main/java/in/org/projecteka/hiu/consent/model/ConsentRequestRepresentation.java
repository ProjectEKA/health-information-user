package in.org.projecteka.hiu.consent.model;

import in.org.projecteka.hiu.clients.Patient;
import lombok.SneakyThrows;
import lombok.Value;
import org.apache.commons.lang3.time.DateUtils;

import java.time.LocalDateTime;

@Value
public class ConsentRequestRepresentation {
    private String id;
    private PatientRepresentation patient;
    private ConsentStatus status;
    private LocalDateTime expiredDate;
    private LocalDateTime createdDate;
    private LocalDateTime approvedDate;

    @SneakyThrows
    public static ConsentRequestRepresentation toConsentRequestRepresentation(
            Patient patient,
            in.org.projecteka.hiu.consent.model.ConsentRequest consentRequest) {
//        String[] dateFormats = {"yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"};
//        var dateErase = DateUtils.parseDate(
//                consentRequest.getPermission().getDataEraseAt(),
//                dateFormats);
        //var createDate = DateUtils.parseDate(consentRequest.getCreatedDate(), dateFormats);

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

