package in.org.projecteka.hiu.patient;

import com.google.common.cache.Cache;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.clients.PatientServiceClient;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.Optional;

@AllArgsConstructor
public class PatientService {
    private PatientServiceClient client;
    private Cache<String, Optional<Patient>> cache;

    public Mono<Patient> patientWith(String id) {
        return cache.asMap().getOrDefault(id, Optional.empty())
                .map(Mono::just)
                .orElseGet(() -> client.patientWith(id).map(patient -> {
                    cache.put(id, Optional.of(patient));
                    return patient;
                }));
    }
}
