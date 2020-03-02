package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import reactor.core.publisher.Flux;

import java.util.stream.StreamSupport;

@AllArgsConstructor
public class HealthInformationRepository {
    private static final String SELECT_HEALTH_INFORMATION = "SELECT data FROM health_information WHERE " +
            "transaction_id=$1";
    private PgPool dbClient;

    public Flux<Object> getHealthInformation(String transactionId) {
        return Flux.create(fluxSink -> dbClient.preparedQuery(SELECT_HEALTH_INFORMATION, Tuple.of(transactionId),
                handler -> {
                    if (handler.failed()) {
                        fluxSink.error(new Exception("Failed to get health information from transaction Id"));
                    } else {
                        StreamSupport.stream(handler.result().spliterator(), false)
                                .map(this::toJsonObject)
                                .forEach(fluxSink::next);
                        fluxSink.complete();
                    }
                }));
    }

    @SneakyThrows
    private Object toJsonObject(Row row) {
        return new ObjectMapper().readTree(row.getString("data"));
    }
}
