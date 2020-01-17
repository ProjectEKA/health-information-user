package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.consentManager.AccessMode;
import in.org.projecteka.hiu.consent.model.consentManager.Consent;
import in.org.projecteka.hiu.consent.model.consentManager.Frequency;
import in.org.projecteka.hiu.consent.model.consentManager.HIU;
import in.org.projecteka.hiu.consent.model.consentManager.Permission;
import in.org.projecteka.hiu.consent.model.consentManager.Purpose;
import in.org.projecteka.hiu.consent.model.consentManager.Requester;
import in.org.projecteka.hiu.consent.model.consentManager.Unit;
import lombok.SneakyThrows;

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
                new Purpose(consent.getPurpose().getCode().getValue(), consent.getPurpose().getCode().name()),
                consent.getPatient(),
                new HIU(hiuId, hiuName),
                new Requester(requesterId),
                consent.getHiTypes(),
                new Permission(AccessMode.VIEW,
                        consent.getPermission().getDateRange(),
                        consent.getPermission().getDataExpiryAt(),
                        new Frequency(Unit.HOUR, 0)));
    }

    @SneakyThrows
    private static Date toDate(String s) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(tz);
        return df.parse(s);
    }
}
