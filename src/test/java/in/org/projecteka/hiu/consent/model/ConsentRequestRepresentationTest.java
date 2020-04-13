package in.org.projecteka.hiu.consent.model;

import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import static in.org.projecteka.hiu.consent.TestBuilders.consentRequest;
import static in.org.projecteka.hiu.consent.TestBuilders.patient;
import static in.org.projecteka.hiu.consent.model.ConsentRequestRepresentation.toConsentRequestRepresentation;
import static org.assertj.core.api.Assertions.assertThat;

class ConsentRequestRepresentationTest {

    @Test
    void returnConsentRequestRepresentation() throws ParseException {
        var expiry = "2021-06-02T10:15:02.325Z";
        var today = "2020-06-02T10:15:02Z";
        var expiryAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").parse(expiry);
        var todayAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(today);
        var consentRequest = consentRequest()
                .permission(Permission.builder().dataExpiryAt(expiry).build())
                .createdDate(today).build();
        var patient = patient().identifier(consentRequest.getPatient().getId()).build();
        var expected = new ConsentRequestRepresentation(
                consentRequest.getId(),
                new PatientRepresentation(patient.getIdentifier(), patient.getFirstName(), patient.getLastName()),
                consentRequest.getStatus(),
                expiryAt,
                todayAt,
                todayAt);

        var consentRequestRepresentation = toConsentRequestRepresentation(patient, consentRequest);

        assertThat(consentRequestRepresentation).isEqualTo(expected);
    }
}