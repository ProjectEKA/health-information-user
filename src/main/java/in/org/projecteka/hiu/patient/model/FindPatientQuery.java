package in.org.projecteka.hiu.patient.model;

import in.org.projecteka.hiu.consent.model.Patient;
import lombok.Value;

@Value
public class FindPatientQuery {
    Patient patient;
    Requester requester;
}
