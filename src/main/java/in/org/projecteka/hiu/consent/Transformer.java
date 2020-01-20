package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.ConsentRequest;
import in.org.projecteka.hiu.consent.model.ConsentStatus;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class Transformer {
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
