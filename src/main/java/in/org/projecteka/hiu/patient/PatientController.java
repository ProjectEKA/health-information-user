package in.org.projecteka.hiu.patient;

import in.org.projecteka.hiu.clients.PatientServiceClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.patient.PatientRepresentation.from;

@RestController
public class PatientController {

    private PatientServiceClient client;

    public PatientController(PatientServiceClient patientServiceClient) {
        this.client = patientServiceClient;
    }

    @GetMapping("patients/{id}")
    public Mono<SearchRepresentation> userWith(@PathVariable(name = "id") String consentManagerUserId) {
        return client.patientWith(consentManagerUserId).map(patient -> new SearchRepresentation(from(patient)));
    }
}