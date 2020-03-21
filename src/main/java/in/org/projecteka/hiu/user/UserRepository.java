package in.org.projecteka.hiu.user;

import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.stream.StreamSupport;

import static in.org.projecteka.hiu.ClientError.dbOperationFailure;

@AllArgsConstructor
public class UserRepository {
    private static final String SELECT_USER_BY_USERNAME = "SELECT username, password, role FROM " +
            "\"user\" WHERE username = $1";

    private PgPool dbClient;

    public Mono<User> with(String username) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        SELECT_USER_BY_USERNAME,
                        Tuple.of(username),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(dbOperationFailure("Failed to fetch consent request"));
                            else {
                                if (handler.failed())
                                    monoSink.error(dbOperationFailure("Failed to fetch consent request"));
                                else {
                                    StreamSupport.stream(handler.result().spliterator(), false)
                                            .map(this::from)
                                            .forEach(monoSink::success);
                                    monoSink.success();
                                }
                                monoSink.success();
                            }
                        }));
    }

    private User from(Row row) {
        return new User(row.getString("username"),
                row.getString("password"),
                row.getString("role") == null
                ? Role.DOCTOR
                : Role.valueOf(row.getString("role").toUpperCase()));
    }
}

