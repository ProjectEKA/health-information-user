package in.org.projecteka.hiu.patient;

import com.google.common.cache.Cache;
import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.common.DelayTimeoutException;
import in.org.projecteka.hiu.patient.model.FindPatientQuery;
import in.org.projecteka.hiu.patient.model.FindPatientRequest;
import in.org.projecteka.hiu.patient.model.PatientSearchGatewayResponse;
import in.org.projecteka.hiu.patient.model.Requester;
import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static in.org.projecteka.hiu.common.CustomScheduler.scheduleThis;
import static in.org.projecteka.hiu.common.ErrorMappings.get;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.just;

@AllArgsConstructor
public class PatientService {
    private static final Logger logger = Logger.getLogger(PatientService.class);
    private static final org.slf4j.Logger factoryLogger = LoggerFactory.getLogger(PatientService.class);
    private final GatewayServiceClient gatewayServiceClient;
    private final Cache<String, Optional<Patient>> cache;
    private final HiuProperties hiuProperties;
    private final GatewayProperties gatewayProperties;
    private final Cache<String, Optional<PatientSearchGatewayResponse>> gatewayResponseCache;

    private static Mono<? extends Patient> apply(PatientSearchGatewayResponse response) {
        if (response.getPatient() != null) {
            return just(response.getPatient().toPatient());
        }
        if (response.getError() != null) {
            return error(get(response.getError().getCode()));
        }
        return error(ClientError.unknownError());
    }

    public Mono<Patient> findPatientWith(String id) {
        return getFromCache(id, () ->
        {
            factoryLogger.info("patient details for: {}", id);
            var cmSuffix = getCmSuffix(id);
            var request = getFindPatientRequest(id);
            return scheduleThis(gatewayServiceClient.findPatientWith(request, cmSuffix))
                    .timeout(Duration.ofMillis(gatewayProperties.getRequestTimeout()))
                    .responseFrom(discard ->
                            Mono.defer(() -> getFromCache(request.getRequestId(), Mono::empty)))
                    .onErrorResume(DelayTimeoutException.class, discard -> error(ClientError.gatewayTimeOut()))
                    .onErrorResume(TimeoutException.class, discard -> error(ClientError.gatewayTimeOut()))
                    .flatMap(PatientService::apply);
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

    private Mono<PatientSearchGatewayResponse> getFromCache(UUID requestId,
                                                            Supplier<Mono<PatientSearchGatewayResponse>> function) {
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
