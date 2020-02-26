package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentArtefactResponse;
import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentNotificationRequest;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.consent.model.Patient;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import org.jeasy.random.EasyRandom;

public class TestBuilders {

    private static final EasyRandom easyRandom = new EasyRandom();

    public static ConsentCreationResponse.ConsentCreationResponseBuilder consentCreationResponse() {
        return easyRandom.nextObject(ConsentCreationResponse.ConsentCreationResponseBuilder.class);
    }

    public static ConsentNotificationRequest.ConsentNotificationRequestBuilder consentNotificationRequest() {
        return easyRandom.nextObject(ConsentNotificationRequest.ConsentNotificationRequestBuilder.class);
    }

    public static ConsentArtefactResponse.ConsentArtefactResponseBuilder consentArtefactResponse() {
        return easyRandom.nextObject(ConsentArtefactResponse.ConsentArtefactResponseBuilder.class);
    }

    public static ConsentArtefactReference.ConsentArtefactReferenceBuilder consentArtefactReference() {
        return easyRandom.nextObject(ConsentArtefactReference.ConsentArtefactReferenceBuilder.class);
    }

    public static Patient.PatientBuilder consentArtefactPatient() {
        return easyRandom.nextObject(Patient.PatientBuilder.class);
    }

    public static in.org.projecteka.hiu.consent.model.ConsentRequest.ConsentRequestBuilder consentRequest() {
        return easyRandom.nextObject(in.org.projecteka.hiu.consent.model.ConsentRequest.ConsentRequestBuilder.class);
    }

    public static ConsentRequestData.ConsentRequestDataBuilder consentRequestDetails() {
        return easyRandom.nextObject(ConsentRequestData.ConsentRequestDataBuilder.class);
    }

    public static ConsentRequest.ConsentRequestBuilder consentRepresentation() {
        return easyRandom.nextObject(ConsentRequest.ConsentRequestBuilder.class);
    }

    public static HiuProperties.HiuPropertiesBuilder hiuProperties() {
        return easyRandom.nextObject(HiuProperties.HiuPropertiesBuilder.class);
    }

    public static in.org.projecteka.hiu.clients.Patient.PatientBuilder patient() {
        return easyRandom.nextObject(in.org.projecteka.hiu.clients.Patient.PatientBuilder.class);
    }

    public static String randomString() {
        return easyRandom.nextObject(String.class);
    }
}
