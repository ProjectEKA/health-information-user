package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.dataprocessor.model.EntryStatus;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static in.org.projecteka.hiu.dataprocessor.model.EntryStatus.ERRORED;
import static in.org.projecteka.hiu.dataprocessor.model.EntryStatus.SUCCEEDED;

@AllArgsConstructor
public class HealthDataRepository {

    private static final Logger logger = LoggerFactory.getLogger(HealthDataRepository.class);

    //TODO: change the column data_flow_part_id to data_part_number
    private static final String INSERT_HEALTH_DATA
            = "INSERT INTO health_information (transaction_id, part_number, data, status, latest_res_date, care_context_reference) VALUES ($1, $2, $3, $4, $5, $6)";
    private final PgPool dbClient;

    private Mono<Void> insertHealthData(String transactionId,
                                        String dataPartNumber,
                                        String resource,
                                        EntryStatus entryStatus, LocalDateTime latestResourceDate, String careContextReference) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(INSERT_HEALTH_DATA)
                        .execute(Tuple.of(transactionId, dataPartNumber, resource,
                                entryStatus.toString(), latestResourceDate, careContextReference),
                                handler -> {
                                    if (handler.failed()) {
                                        logger.error(handler.cause().getMessage(), handler.cause());
                                        monoSink.error(new Exception("Failed to insert health information"));
                                        return;
                                    }
                                    monoSink.success();
                                }));
    }

    public Mono<Void> insertErrorFor(String transactionId, String dataPartNumber, String careContextReference) {
        return insertHealthData(transactionId, dataPartNumber, "", ERRORED, null, careContextReference);
    }

    public Mono<Void> insertDataFor(String transactionId, String dataPartNumber, String resource, LocalDateTime latestResourceDate, String careContextReference) {
        return insertHealthData(transactionId, dataPartNumber, resource, SUCCEEDED, latestResourceDate, careContextReference);
    }
}
