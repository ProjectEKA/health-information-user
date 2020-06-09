package in.org.projecteka.hiu.patient;

import com.google.common.cache.Cache;
import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.GatewayServiceProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.clients.PatientServiceClient;
import in.org.projecteka.hiu.common.CentralRegistry;
import in.org.projecteka.hiu.common.DelayTimeoutException;
import in.org.projecteka.hiu.common.ErrorMappings;
import in.org.projecteka.hiu.patient.model.FindPatientQuery;
import in.org.projecteka.hiu.patient.model.FindPatientRequest;
import in.org.projecteka.hiu.patient.model.PatientSearchGatewayResponse;
import in.org.projecteka.hiu.patient.model.Requester;
import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static in.org.projecteka.hiu.common.CustomScheduler.scheduleThis;
import static java.lang.String.format;

@AllArgsConstructor
public class PatientService {
    private static final Logger logger = Logger.getLogger(PatientService.class);
    private final PatientServiceClient client;
    private final GatewayServiceClient gatewayServiceClient;
    private final Cache<String, Optional<Patient>> cache;
    private final CentralRegistry centralRegistry;
    private final HiuProperties hiuProperties;
    private final GatewayServiceProperties gatewayServiceProperties;
    private final Cache<String, Optional<PatientSearchGatewayResponse>> gatewayResponseCache;

    @Deprecated
    public Mono<Patient> patientWith(String id) {
        return cache.asMap().getOrDefault(id, Optional.empty())
                .map(Mono::just)
                .orElseGet(() ->
                        centralRegistry.token()
                                .flatMap(token -> client.patientWith(id, token))
                                .map(patientRep -> {
                                    cache.put(id, Optional.of(patientRep.toPatient()));
                                    logger.debug(format("Updated cache for patient %s with id: %s",
                                            patientRep.toPatient().getFirstName(),
                                            patientRep.toPatient().getIdentifier()));
                                    return patientRep.toPatient();
                                }));
    }

    public Mono<Patient> findPatientWith(String id) {
        return getFromCache(id, () ->
        {
            var cmSuffix = getCmSuffix(id);
            var request = getFindPatientRequest(id);

            return scheduleThis(gatewayServiceClient.findPatientWith(request, cmSuffix))
                    .timeout(Duration.ofMillis(gatewayServiceProperties.getRequestTimeout()))
                    .responseFrom(discard ->
                            Mono.defer(() -> getFromCache(request.getRequestId(), Mono::empty)))
                    .onErrorResume(DelayTimeoutException.class, discard -> Mono.error(ClientError.gatewayTimeOut()))
                    .onErrorResume(TimeoutException.class, discard -> Mono.error(ClientError.gatewayTimeOut()))
                    .flatMap(response -> {
                        if (response.getError() != null) {
                            return Mono.error(ErrorMappings.get(response.getError().getCode()));
                        }
                        if (response.getPatient() != null) {
                            return Mono.just(response.getPatient().toPatient());
                        }
                        return Mono.error(ClientError.unknownError());
                    });
        });
    }

    private FindPatientRequest getFindPatientRequest(String id) {
        var requestId = UUID.randomUUID();
        var timestamp = java.time.Instant.now().toString();
        var patient = new in.org.projecteka.hiu.consent.model.Patient(id);
        var requester = new Requester("HIU", hiuProperties.getId());
        var query = new FindPatientQuery(patient, requester);
        return new FindPatientRequest(requestId, timestamp, query);
    }

    private Mono<Patient> getFromCache(String key, Supplier<Mono<Patient>> function) {
        return cache.asMap().getOrDefault(key, Optional.empty())
                .map(Mono::just)
                .orElseGet(function);
    }

    private Mono<PatientSearchGatewayResponse> getFromCache(UUID requestId, Supplier<Mono<PatientSearchGatewayResponse>> function) {
        return gatewayResponseCache.asMap().getOrDefault(requestId.toString(), Optional.empty())
                .map(Mono::just)
                .orElseGet(function);
    }

    private String getCmSuffix(String id) {
        return id.split("@")[1];
    }

    public Mono<Void> onFindPatient(PatientSearchGatewayResponse response) {
        if (response.getError() != null) {
            logger.error(String.format("[PatientService] Received error response from find-patient. HIU RequestId=%s, Error code = %d, message=%s",
                    response.getResp().getRequestId(),
                    response.getError().getCode(),
                    response.getError().getMessage()));
        }
        if (response.getPatient() != null) {
            cache.put(response.getPatient().getId(), Optional.of(response.getPatient().toPatient()));
        }
        gatewayResponseCache.put(response.getResp().getRequestId(),Optional.of(response));
        return Mono.empty();
    }
}
