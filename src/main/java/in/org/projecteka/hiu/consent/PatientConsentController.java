package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.consent.model.CareContextInfoRequest;
import in.org.projecteka.hiu.consent.model.PatientConsentRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

import static in.org.projecteka.hiu.common.Constants.APP_PATH_PATIENT_CONSENT_REQUEST;
import static in.org.projecteka.hiu.common.Constants.INTERNAL_PATH_PATIENT_CARE_CONTEXT_INFO;


@RestController
@AllArgsConstructor
public class PatientConsentController {
    private final ConsentService consentService;

    @PostMapping(APP_PATH_PATIENT_CONSENT_REQUEST)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Map<String, String>> postPatientConsentRequest(@RequestBody PatientConsentRequest consentRequest) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (Caller) securityContext.getAuthentication().getPrincipal())
                .map(Caller::getUsername)
                .flatMap(requesterId ->
                        consentService.handlePatientConsentRequest(requesterId, consentRequest)
                );
    }

    @GetMapping(INTERNAL_PATH_PATIENT_CARE_CONTEXT_INFO)
    @ResponseStatus(HttpStatus.OK)
    public Mono<List<Map<String, Object>>> careContextStatus(@RequestBody @Valid CareContextInfoRequest request) {
        return Mono.from(consentService.getLatestCareContextResourceDates(request.getPatientId(), request.getHipId()));
    }
}
