package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.dataprocessor.model.EntryStatus;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.dataprocessor.model.EntryStatus.ERRORED;
import static in.org.projecteka.hiu.dataprocessor.model.EntryStatus.SUCCEEDED;

@AllArgsConstructor
public class HealthDataRepository {
    //TODO: change the column data_flow_part_id to data_part_number
    private static final String INSERT_HEALTH_DATA
            = "INSERT INTO health_information (transaction_id, part_number, data, status) VALUES ($1, $2, $3, $4)";
    private final PgPool dbClient;

    private Mono<Void> insertHealthData(String transactionId,
                                        String dataPartNumber,
                                        String resource,
                                        EntryStatus entryStatus) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(INSERT_HEALTH_DATA)
                        .execute(Tuple.of(transactionId, dataPartNumber, resource, entryStatus.toString()),
                                handler -> {
                                    if (handler.failed()) {
                                        monoSink.error(new Exception("Failed to insert health information"));
                                        return;
                                    }
                                    monoSink.success();
                                }));
    }

    public Mono<Void> insertErrorFor(String transactionId, String dataPartNumber) {
        return insertHealthData(transactionId, dataPartNumber, "", ERRORED);
    }

    public Mono<Void> insertDataFor(String transactionId, String dataPartNumber, String resource) {
        return insertHealthData(transactionId, dataPartNumber, resource, SUCCEEDED);
    }
}
