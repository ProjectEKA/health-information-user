package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.ConsentRequest;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.consent.model.consentmanager.AccessMode;
import in.org.projecteka.hiu.consent.model.consentmanager.Consent;
import in.org.projecteka.hiu.consent.model.consentmanager.Frequency;
import in.org.projecteka.hiu.consent.model.consentmanager.HIU;
import in.org.projecteka.hiu.consent.model.consentmanager.Permission;
import in.org.projecteka.hiu.consent.model.consentmanager.Purpose;
import in.org.projecteka.hiu.consent.model.consentmanager.Requester;
import in.org.projecteka.hiu.consent.model.consentmanager.Unit;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class Transformer {

    public static Consent toConsentManagerConsent(String requesterId,
                                                  in.org.projecteka.hiu.consent.model.Consent consent,
                                                  String hiuId,
                                                  String hiuName) {
        return new Consent(
                new Purpose(consent.getPurpose().getCode().getValue(),
                        consent.getPurpose().getCode().name()),
                consent.getPatient(),
                new HIU(hiuId, hiuName),
                new Requester(requesterId),
                consent.getHiTypes(),
                new Permission(AccessMode.VIEW,
                        consent.getPermission().getDateRange(),
                        consent.getPermission().getDataExpiryAt(),
                        new Frequency(Unit.HOUR, 0)));
    }

    public static ConsentRequest toConsentRequest(
            String id,
            String requesterId,
            in.org.projecteka.hiu.consent.model.Consent consent) {
        return new ConsentRequest(id,
                requesterId,
                consent.getPatient(),
                consent.getPurpose(),
                consent.getHiTypes(),
                consent.getPermission(),
                ConsentStatus.REQUESTED,
                getCurrentDate()
                );
    }

    private static String getCurrentDate() {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(tz);
        return df.format(new Date());
    }

}
