package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static in.org.projecteka.hiu.ClientError.dbOperationFailure;

@AllArgsConstructor
public class HealthInformationRepository {
    private static final String SELECT_HEALTH_INFORMATION = "SELECT data, status FROM health_information WHERE " +
            "transaction_id=$1";
    private static final String SELECT_HEALTH_INFO_FOR_MULTIPLE_TRANSACTIONS = "SELECT data, status, transaction_id " +
            "FROM health_information WHERE transaction_id in (%s) " +
            "ORDER BY date_created DESC LIMIT $1 OFFSET $2";
    private static final String DELETE_HEALTH_INFO_FOR_EXPIRED_CONSENT = "DELETE FROM health_information WHERE transaction_id=$1";

    private final PgPool dbClient;
    private final Logger logger = LoggerFactory.getLogger(HealthInformationRepository.class);

    public Flux<Map<String, Object>> getHealthInformation(String transactionId) {
        return Flux.create(fluxSink -> dbClient.preparedQuery(SELECT_HEALTH_INFORMATION)
                .execute(Tuple.of(transactionId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                fluxSink.error(
                                        dbOperationFailure("Failed to get health information from transaction Id"));
                                return;
                            }
                            for (Row row : handler.result()) {
                                try {
                                    fluxSink.next(toHealthInfo(row));
                                } catch (JsonProcessingException e) {
                                    logger.error(e.getMessage(), e);
                                    fluxSink.error(dbOperationFailure(e.getOriginalMessage()));
                                }
                            }
                            fluxSink.complete();
                        }));
    }

    public Mono<Void> deleteHealthInformation(String transactionId) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(DELETE_HEALTH_INFO_FOR_EXPIRED_CONSENT)
                        .execute(Tuple.of(transactionId),
                                handler -> {
                                    if (handler.failed()) {
                                        logger.error(handler.cause().getMessage(), handler.cause());
                                        monoSink.error(new Exception("Failed to delete health information"));
                                        return;
                                    }
                                    monoSink.success();
                                }));
    }

    public Flux<Map<String, Object>> getHealthInformation(List<String> transactionIds, int limit, int offset) {
        var generatedQuery = String.format(SELECT_HEALTH_INFO_FOR_MULTIPLE_TRANSACTIONS, joinByComma(transactionIds));
        return Flux.create(fluxSink -> dbClient.preparedQuery(generatedQuery)
                .execute(Tuple.of(limit, offset),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                fluxSink.error(
                                        dbOperationFailure("Failed to get health information for given transaction ids"));
                                return;
                            }
                            for (Row row : handler.result()) {
                                try {
                                    fluxSink.next(toHealthInfo(row));
                                } catch (JsonProcessingException e) {
                                    logger.error(e.getMessage(), e);
                                    fluxSink.error(dbOperationFailure(e.getOriginalMessage()));
                                }
                            }
                            fluxSink.complete();
                        }));
    }

    private Map<String, Object> toHealthInfo(Row row) throws JsonProcessingException {
        String data = row.getString("data");
        Map<String, Object> healthInfo = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(WRITE_DATES_AS_TIMESTAMPS, false);
        healthInfo.put("data", objectMapper.readTree(data != null ? data : ""));
        healthInfo.put("status", row.getString("status"));
        healthInfo.put("transaction_id", row.getString("transaction_id"));
        return healthInfo;
    }

    private String joinByComma(List<String> list) {
        return String.join(", ", list.stream().map(e -> String.format("'%s'", e)).collect(Collectors.toList()));
    }
}
