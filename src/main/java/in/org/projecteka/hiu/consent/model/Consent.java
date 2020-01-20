package in.org.projecteka.hiu.consent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.org.projecteka.hiu.consent.model.consentmanager.AccessMode;
import in.org.projecteka.hiu.consent.model.consentmanager.HIU;
import in.org.projecteka.hiu.consent.model.consentmanager.Requester;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static in.org.projecteka.hiu.consent.model.consentmanager.Frequency.ZERO_HOUR;

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

    private static String getCurrentDate() {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(tz);
        return df.format(new Date());
    }

    public in.org.projecteka.hiu.consent.model.consentmanager.Consent to(String requesterId,
                                                                         String hiuId,
                                                                         String hiuName) {
        return new in.org.projecteka.hiu.consent.model.consentmanager.Consent(
                new in.org.projecteka.hiu.consent.model.consentmanager.Purpose(
                        getPurpose().getCode().getValue(),
                        getPurpose().getCode().name()),
                getPatient(),
                new HIU(hiuId, hiuName),
                new Requester(requesterId),
                getHiTypes(),
                new in.org.projecteka.hiu.consent.model.consentmanager.Permission(AccessMode.VIEW,
                        getPermission().getDateRange(),
                        getPermission().getDataExpiryAt(),
                        ZERO_HOUR));
    }

    public ConsentRequest toConsentRequest(String id, String requesterId) {
        return new ConsentRequest(id,
                requesterId,
                getPatient(),
                getPurpose(),
                getHiTypes(),
                getPermission(),
                ConsentStatus.REQUESTED,
                getCurrentDate()
        );
    }
}
