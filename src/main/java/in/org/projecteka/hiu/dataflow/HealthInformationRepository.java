package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

@AllArgsConstructor
public class HealthInformationRepository {
    private static final String SELECT_HEALTH_INFORMATION = "SELECT data, status FROM health_information WHERE " +
            "transaction_id=$1";
    private PgPool dbClient;

    public Flux<Map<String, Object>> getHealthInformation(String transactionId) {
        return Flux.create(fluxSink -> dbClient.preparedQuery(SELECT_HEALTH_INFORMATION)
                .execute(Tuple.of(transactionId),
                        handler -> {
                            if (handler.failed()) {
                                fluxSink.error(new Exception("Failed to get health information from transaction Id"));
                                return;
                            }
                            StreamSupport.stream(handler.result().spliterator(), false)
                                    .map(this::toHealthInfo)
                                    .forEach(fluxSink::next);
                            fluxSink.complete();
                        }));
    }

    @SneakyThrows
    private Map<String, Object> toHealthInfo(Row row) {
        String data = row.getString("data");
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("data", new ObjectMapper().readTree(data != null ? data : ""));
        healthInfo.put("status", row.getString("status"));
        return healthInfo;
    }
}
