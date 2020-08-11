package in.org.projecteka.hiu.patient;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.common.DelayTimeoutException;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import in.org.projecteka.hiu.patient.model.FindPatientQuery;
import in.org.projecteka.hiu.patient.model.FindPatientRequest;
import in.org.projecteka.hiu.patient.model.PatientSearchGatewayResponse;
import in.org.projecteka.hiu.patient.model.Requester;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static in.org.projecteka.hiu.ClientError.gatewayTimeOut;
import static in.org.projecteka.hiu.ClientError.unknownError;
import static in.org.projecteka.hiu.ErrorCode.PATIENT_NOT_FOUND;
import static in.org.projecteka.hiu.common.Constants.getCmSuffix;
import static in.org.projecteka.hiu.common.CustomScheduler.scheduleThis;
import static in.org.projecteka.hiu.common.ErrorMappings.get;
import static java.time.Duration.ofMillis;
import static org.slf4j.LoggerFactory.getLogger;
import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.just;
import static reactor.core.publisher.Mono.justOrEmpty;

@AllArgsConstructor
public class PatientService {
    private static final Logger logger = getLogger(PatientService.class);
    private final GatewayServiceClient gatewayServiceClient;
    private final CacheAdapter<String, Patient> cache;
    private final HiuProperties hiuProperties;
    private final GatewayProperties gatewayProperties;
    private final CacheAdapter<String, PatientSearchGatewayResponse> gatewayResponseCache;

    private static Mono<Patient> apply(PatientSearchGatewayResponse response) {
        if (response.getPatient() != null) {
            return just(response.getPatient().toPatient());
        }
        if (response.getError() != null) {
            logger.error("Error received from gateway: {}", response.getError());
            return error(get(response.getError().getCode()));
        }
        logger.error("Gateway response: {}", response);
        return error(unknownError());
    }

    public Mono<Patient> tryFind(String id) {
        return findPatientWith(id)
                .onErrorResume(error -> error instanceof ClientError &&
                                ((ClientError) error).getError().getError().getCode() == PATIENT_NOT_FOUND,
                        error -> {
                            logger.error("Consent request created for unknown user.");
                            logger.error(error.getMessage(), error);
                            return empty();
                        });
    }

    public Mono<Patient> findPatientWith(String id) {
        return getFromCache(id, () ->
        {
            logger.info("about to get patient details from CM for: {}", id);
            var cmSuffix = getCmSuffix(id);
            var request = getFindPatientRequest(id);
            return scheduleThis(gatewayServiceClient.findPatientWith(request, cmSuffix))
                    .timeout(ofMillis(gatewayProperties.getRequestTimeout()))
                    .responseFrom(discard -> defer(() -> getFromCache(request.getRequestId())))
                    .onErrorResume(DelayTimeoutException.class, discard -> error(gatewayTimeOut()))
                    .onErrorResume(TimeoutException.class, discard -> error(gatewayTimeOut()))
                    .flatMap(PatientService::apply);
        });
    }

    private FindPatientRequest getFindPatientRequest(String id) {
        var requestId = UUID.randomUUID();
        var timestamp = LocalDateTime.now(ZoneOffset.UTC);
        var patient = new in.org.projecteka.hiu.consent.model.Patient(id);
        var requester = new Requester("HIU", hiuProperties.getId());
        var query = new FindPatientQuery(patient, requester);
        return new FindPatientRequest(requestId, timestamp, query);
    }

    private Mono<Patient> getFromCache(String key, Supplier<Mono<Patient>> function) {
        return cache.get(key).switchIfEmpty(defer(function));
    }

    private Mono<PatientSearchGatewayResponse> getFromCache(UUID requestId) {
        return gatewayResponseCache.get(requestId.toString());
    }

    public Mono<Void> onFindPatient(PatientSearchGatewayResponse response) {
        if (response.getError() != null) {
            logger.error("[PatientService] Received error response from find-patient." +
                            "HIU RequestId={}, Error code = {}, message={}",
                    response.getResp().getRequestId(),
                    response.getError().getCode(),
                    response.getError().getMessage());
        }

        return justOrEmpty(response.getPatient())
                .flatMap(patient -> cache.put(patient.getId(), patient.toPatient()))
                .then(defer(() -> gatewayResponseCache.put(response.getResp().getRequestId(), response)));
    }
}
