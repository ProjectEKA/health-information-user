package in.org.projecteka.hiu.dataprocessor;

import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class HealthDataRepository {
    //TODO: change the column data_flow_part_id to data_part_number
    private static final String INSERT_HEALTH_DATA
            = "INSERT INTO health_information (transaction_id, part_number, data) VALUES ($1, $2, $3)";
    private PgPool dbClient;

    public Mono<Void> insertHealthData(String transactionId, String dataPartNumber, String resource) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        INSERT_HEALTH_DATA,
                        Tuple.of(transactionId, dataPartNumber, resource),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(new Exception("Failed to insert health information"));
                            else
                                monoSink.success();
                        })
        );
    }
}
