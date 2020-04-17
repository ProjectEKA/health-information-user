package in.org.projecteka.hiu.patient;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.patient.PatientRepresentation.from;

@RestController
@AllArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @GetMapping("patients/{id}")
    public Mono<SearchRepresentation> userWith(@PathVariable(name = "id") String consentManagerUserId) {
        return patientService.patientWith(consentManagerUserId).map(patient -> new SearchRepresentation(from(patient)));
    }
}