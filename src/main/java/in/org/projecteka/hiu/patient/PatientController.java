package in.org.projecteka.hiu.patient;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class PatientController {

    private PatientServiceClient client;

    public PatientController(PatientServiceClient patientServiceClient) {
        this.client = patientServiceClient;
    }

    @GetMapping("patients/{id}")
    public Mono<SearchRepresentation> userWith(@PathVariable(name = "id", required = true) String consentManagerUserId) {
        return client.patientWith(consentManagerUserId);
    }
}