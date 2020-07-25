package in.org.projecteka.hiu.patient;

import in.org.projecteka.hiu.common.Constants;
import in.org.projecteka.hiu.patient.model.PatientSearchGatewayResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.patient.PatientRepresentation.from;

@RestController
@AllArgsConstructor
public class PatientController {

    private static final String APP_PATH_PATIENTS_ID = "/v1/patients/{id}";
    private final PatientService patientService;

    @GetMapping(APP_PATH_PATIENTS_ID)
    public Mono<SearchRepresentation> findUserWith(@PathVariable(name = "id") String consentManagerUserId) {
        return patientService.findPatientWith(consentManagerUserId).map(patient -> new SearchRepresentation(from(patient)));
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping(Constants.PATH_PATIENTS_ON_FIND)
    public Mono<Void> onFindUser(@RequestBody PatientSearchGatewayResponse patientSearchGatewayResponse) {
        return patientService.onFindPatient(patientSearchGatewayResponse);
    }

    @GetMapping("/cm/hello")
    public Mono<String> helloWorld(){
        return Mono.just("Hello World");
    }
}
