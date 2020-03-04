package in.org.projecteka.hiu.patient;

import com.google.common.cache.Cache;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.clients.PatientServiceClient;
import in.org.projecteka.hiu.common.CentralRegistry;
import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static java.lang.String.format;

@AllArgsConstructor
public class PatientService {
    private static final Logger logger = Logger.getLogger(PatientService.class);
    private final PatientServiceClient client;
    private final Cache<String, Optional<Patient>> cache;
    private final CentralRegistry centralRegistry;

    public Mono<Patient> patientWith(String id) {
        return cache.asMap().getOrDefault(id, Optional.empty())
                .map(Mono::just)
                .orElseGet(() ->
                        centralRegistry.token()
                                .flatMap(token -> client.patientWith(id, token))
                                .map(patient -> {
                                    cache.put(id, Optional.of(patient));
                                    logger.debug(format("Updated cache for patient %s with id: %s",
                                            patient.getFirstName(),
                                            patient.getIdentifier()));
                                    return patient;
                                }));
    }
}
