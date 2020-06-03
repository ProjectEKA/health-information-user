package in.org.projecteka.hiu.consent.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static in.org.projecteka.hiu.consent.TestBuilders.consentRequest;
import static in.org.projecteka.hiu.consent.TestBuilders.patient;
import static in.org.projecteka.hiu.consent.model.ConsentRequestRepresentation.toConsentRequestRepresentation;
import static org.assertj.core.api.Assertions.assertThat;

class ConsentRequestRepresentationTest {

    @Test
    void returnConsentRequestRepresentation() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd['T'HH[:mm][:ss][.SSS]]");
        var expiryAt = LocalDateTime.parse("2021-06-02T10:15:02.325", formatter);
        var todayAt = LocalDateTime.parse("2020-06-02T10:15:02", formatter);
        var consentRequest = consentRequest()
                .permission(Permission.builder().dataEraseAt(expiryAt).build())
                .createdDate(todayAt).build();
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