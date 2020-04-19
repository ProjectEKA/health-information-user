package in.org.projecteka.hiu.user;

import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.ClientError.dbOperationFailure;

@AllArgsConstructor
public class UserRepository {
    private static final String SELECT_USER_BY_USERNAME = "SELECT username, password, role FROM " +
            "\"user\" WHERE username = $1";
    private static final String INSERT_USER = "Insert into \"user\" values ($1, $2, $3)";

    private final PgPool dbClient;
    private final Logger logger = Logger.getLogger(UserRepository.class);

    public Mono<User> with(String username) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(SELECT_USER_BY_USERNAME,
                        Tuple.of(username),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause());
                                monoSink.error(dbOperationFailure("Failed to fetch user."));
                                return;
                            }
                            var iterator = handler.result().iterator();
                            if (!iterator.hasNext()) {
                                monoSink.success();
                                return;
                            }
                            monoSink.success(tryFrom(iterator.next()));
                        }));
    }

    public Mono<Void> save(User user) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        INSERT_USER,
                        Tuple.of(user.getUsername(), user.getPassword(), user.getRole().toString()),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause());
                                monoSink.error(dbOperationFailure("Failed to save user."));
                                return;
                            }
                            monoSink.success();
                        }));
    }

    private User tryFrom(Row row) {
        try {
            return new User(row.getString("username"),
                    row.getString("password"),
                    row.getString("role") == null
                    ? Role.DOCTOR
                    : Role.valueOf(row.getString("role").toUpperCase()));
        } catch (Exception e) {
            logger.error(e);
            return null;
        }
    }
}

