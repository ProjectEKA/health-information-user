package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

import static in.org.projecteka.hiu.ClientError.dbOperationFailure;

@AllArgsConstructor
public class HealthInformationRepository {
    private static final Logger logger = LoggerFactory.getLogger(HealthInformationRepository.class);
    private static final String SELECT_HEALTH_INFORMATION = "SELECT data, status FROM health_information WHERE " +
            "transaction_id=$1";
    private final PgPool dbClient;

    public Flux<Map<String, Object>> getHealthInformation(String transactionId) {
        return Flux.create(fluxSink -> dbClient.preparedQuery(SELECT_HEALTH_INFORMATION)
                .execute(Tuple.of(transactionId),
                        handler -> {
                            if (handler.failed()) {
                                fluxSink.error(
                                        dbOperationFailure("Failed to get health information from transaction Id"));
                            } else {
                                for (Row row : handler.result()) {
                                    try {
                                        fluxSink.next(toHealthInfo(row));
                                    } catch (JsonProcessingException e) {
                                        logger.error(e.getMessage(), e);
                                        fluxSink.error(dbOperationFailure(e.getOriginalMessage()));
                                    }
                                }
                                fluxSink.complete();
                            }
                        }));
    }

    private Map<String, Object> toHealthInfo(Row row) throws JsonProcessingException {
        String data = row.getString("data");
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("data", new ObjectMapper().readTree(data != null ? data : ""));
        healthInfo.put("status", row.getString("status"));
        return healthInfo;
    }
}
