package in.org.projecteka.hiu;

import in.org.projecteka.hiu.consent.ConsentManagerClient;
import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.consent.ConsentService;
import in.org.projecteka.hiu.patient.PatientServiceClient;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class HiuConfiguration {
    @Bean
    public PatientServiceClient patientServiceClient(
            WebClient.Builder builder,
            ConsentManagerServiceProperties consentManagerServiceProperties,
            HiuProperties hiuProperties) {
        return new PatientServiceClient(builder, consentManagerServiceProperties, hiuProperties);
    }

    @Bean
    public PgPool dbConnectionPool(DatabaseProperties dbProps) {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setPort(dbProps.getPort())
                .setHost(dbProps.getHost())
                .setDatabase(dbProps.getSchema())
                .setUser(dbProps.getUser())
                .setPassword(dbProps.getPassword());

        PoolOptions poolOptions =
                new PoolOptions().setMaxSize(dbProps.getPoolSize());

        return PgPool.pool(connectOptions, poolOptions);
    }

    @Bean
    public ConsentService consentService(
            WebClient.Builder builder,
            ConsentManagerServiceProperties consentManagerServiceProperties,
            HiuProperties hiuProperties,
            ConsentRepository consentRepository) {
        return new ConsentService(
                new ConsentManagerClient(builder, consentManagerServiceProperties, hiuProperties),
                hiuProperties,
                consentRepository);
    }

    @Bean
    public ConsentRepository consentRepository(PgPool pgPool) {
        return new ConsentRepository(pgPool);
    }
}