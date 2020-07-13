package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.PatientConsentRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static in.org.projecteka.hiu.ClientError.dbOperationFailure;
import static in.org.projecteka.hiu.common.Serializer.from;

@AllArgsConstructor
public class PatientConsentRepository {

    private static final Logger logger = LoggerFactory.getLogger(PatientConsentRepository.class);

    private static final String INSERT_PATIENT_CONSENT_REQUEST = "INSERT INTO " +
            "patient_consent_request (patient_consent_request, patient_consent_request_id) VALUES ($1, $2)";


    private static final String INSERT_PATIENT_CONSENT_REQUEST_MAPPING = "INSERT INTO " +
            "patient_consent_request_mapping (patient_consent_request_id, consent_request_id) VALUES ($1, $2)";

    private final PgPool dbClient;

    public Mono<Void> insertConsentRequestToGateway(PatientConsentRequest consentRequest, UUID requestId) {
        return Mono.create(monoSink -> dbClient.preparedQuery(INSERT_PATIENT_CONSENT_REQUEST)
                .execute(Tuple.of(new JsonObject(from(consentRequest)), requestId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to insert to patient consent request"));
                                return;
                            }
                            monoSink.success();
                        }));
    }


    public Mono<Void> insertPatientConsentRequestMapping(UUID patientConsentRequestId, UUID consentRequestId) {
        return Mono.create(monoSink -> dbClient.preparedQuery(INSERT_PATIENT_CONSENT_REQUEST_MAPPING)
                .execute(Tuple.of(patientConsentRequestId, consentRequestId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to insert to patient consent request mapping"));
                                return;
                            }
                            monoSink.success();
                        }));
    }
}

