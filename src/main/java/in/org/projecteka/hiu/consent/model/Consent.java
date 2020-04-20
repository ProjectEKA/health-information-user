package in.org.projecteka.hiu.consent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.org.projecteka.hiu.consent.ConceptLookup;
import in.org.projecteka.hiu.consent.model.consentmanager.AccessMode;
import in.org.projecteka.hiu.consent.model.consentmanager.HIU;
import in.org.projecteka.hiu.consent.model.consentmanager.Requester;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

import static in.org.projecteka.hiu.consent.model.consentmanager.Frequency.ONE_HOUR;

@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@Builder
@NoArgsConstructor
@Data
public class Consent {
    private Patient patient;
    private Purpose purpose;
    private List<HIType> hiTypes;
    private Permission permission;

    public static Date getCurrentDate() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(
                "yyyy-MM-dd['T'[HH][:mm][:ss][.SSS][X]]");
        ZonedDateTime zdt = ZonedDateTime.now(ZoneId.of("UTC"));
        return Date.from(ZonedDateTime.parse(dateTimeFormatter.format(zdt), dateTimeFormatter).toInstant());
    }

    public in.org.projecteka.hiu.consent.model.consentmanager.Consent to(String requesterId,
                                                                         String hiuId,
                                                                         String hiuName,
                                                                         String consentNotificationUrl, ConceptLookup conceptLookup) {
        return new in.org.projecteka.hiu.consent.model.consentmanager.Consent(
                new in.org.projecteka.hiu.consent.model.consentmanager.Purpose(
                        getPurpose().getCode(),
                        conceptLookup.getPurposeDescription(getPurpose().getCode())),
                getPatient(),
                new HIU(hiuId, hiuName),
                new Requester(requesterId),
                getHiTypes(),
                new in.org.projecteka.hiu.consent.model.consentmanager.Permission(
                        AccessMode.VIEW,
                        getPermission().getDateRange(),
                        getPermission().getDataEraseAt(),
                        ONE_HOUR),
                consentNotificationUrl);
    }

    public ConsentRequest toConsentRequest(String id, String requesterId, String consentNotificationUrl) {
        return new ConsentRequest(
                id,
                requesterId,
                getPatient(),
                getPurpose(),
                getHiTypes(),
                getPermission(),
                ConsentStatus.REQUESTED,
                getCurrentDate(),
                consentNotificationUrl);
    }
}
