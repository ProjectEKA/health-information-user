package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.dataflow.model.Entry;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.stream.StreamSupport;

@AllArgsConstructor
public class HealthInformationRepository {
    private static final String SELECT_HEALTH_INFORMATION = "SELECT health_information FROM health_information WHERE " +
            "transaction_id=$1";
    private PgPool dbClient;

    public Flux<Entry> getHealthInformation(String transactionId) {
        return Flux.create(fluxSink -> dbClient.preparedQuery(SELECT_HEALTH_INFORMATION, Tuple.of(transactionId),
                handler -> {
                    if (handler.failed()) {
                        fluxSink.error(new Exception("Failed to get health information from transaction Id"));
                    } else {
                        StreamSupport.stream(handler.result().spliterator(), false)
                                .map(row -> (JsonObject) row.getValue("health_information"))
                                .map(json -> json.mapTo(Entry.class))
                                .forEach(fluxSink::next);
                        fluxSink.complete();
                    }
                }));
    }
}
