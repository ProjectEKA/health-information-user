package in.org.projecteka.hiu.consent;

import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

import static in.org.projecteka.hiu.ClientError.dbOperationFailure;

@AllArgsConstructor
public class PatientConsentRepository {

    private static final Logger logger = LoggerFactory.getLogger(PatientConsentRepository.class);

    private static final String INSERT_PATIENT_CONSENT_REQUEST = "INSERT INTO " +
            "patient_consent_request (data_request_id, hip_id) VALUES ($1, $2)";

    private static final String UPDATE_PATIENT_CONSENT_REQUEST = "UPDATE " +
            "patient_consent_request SET consent_request_id=$2, date_modified=$3 WHERE data_request_id=$1";

    private final PgPool dbClient;

    public Mono<Void> insertPatientConsentRequest(UUID dataRequestId, String hipId) {
        return Mono.create(monoSink -> dbClient.preparedQuery(INSERT_PATIENT_CONSENT_REQUEST)
                .execute(Tuple.of(dataRequestId, hipId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to insert to patient consent request"));
                                return;
                            }
                            monoSink.success();
                        }));
    }


    public Mono<Void> updatePatientConsentRequest(UUID dataRequestId, UUID consentRequestId, LocalDateTime now) {
        return Mono.create(monoSink -> dbClient.preparedQuery(UPDATE_PATIENT_CONSENT_REQUEST)
                .execute(Tuple.of(dataRequestId, consentRequestId, now),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to update patient consent request"));
                                return;
                            }
                            monoSink.success();
                        }));
    }
}

