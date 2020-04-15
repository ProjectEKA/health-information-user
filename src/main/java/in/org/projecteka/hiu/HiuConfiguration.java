package in.org.projecteka.hiu;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import in.org.projecteka.hiu.clients.CentralRegistryClient;
import in.org.projecteka.hiu.clients.HealthInformationClient;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.clients.PatientServiceClient;
import in.org.projecteka.hiu.common.Authenticator;
import in.org.projecteka.hiu.common.Base64Authenticator;
import in.org.projecteka.hiu.common.CentralRegistry;
import in.org.projecteka.hiu.common.CentralRegistryTokenVerifier;
import in.org.projecteka.hiu.common.UserAuthenticator;
import in.org.projecteka.hiu.common.UserBase64Authenticator;
import in.org.projecteka.hiu.consent.ConsentManagerClient;
import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.consent.ConsentService;
import in.org.projecteka.hiu.consent.DataFlowRequestPublisher;
import in.org.projecteka.hiu.consent.HealthInformationPublisher;
import in.org.projecteka.hiu.dataflow.DataAvailabilityPublisher;
import in.org.projecteka.hiu.dataflow.DataFlowClient;
import in.org.projecteka.hiu.dataflow.DataFlowRepository;
import in.org.projecteka.hiu.dataflow.DataFlowRequestListener;
import in.org.projecteka.hiu.dataflow.DataFlowService;
import in.org.projecteka.hiu.dataflow.DataFlowServiceProperties;
import in.org.projecteka.hiu.dataflow.Decryptor;
import in.org.projecteka.hiu.dataflow.HealthInfoManager;
import in.org.projecteka.hiu.dataflow.HealthInformationRepository;
import in.org.projecteka.hiu.dataflow.LocalDataStore;
import in.org.projecteka.hiu.dataprocessor.DataAvailabilityListener;
import in.org.projecteka.hiu.dataprocessor.HealthDataRepository;
import in.org.projecteka.hiu.patient.PatientService;
import in.org.projecteka.hiu.user.JWTGenerator;
import in.org.projecteka.hiu.user.SessionService;
import in.org.projecteka.hiu.user.UserRepository;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ResourceProperties;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.URL;
import java.security.SecureRandom;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Configuration
public class HiuConfiguration {
    public static final String DATA_FLOW_REQUEST_QUEUE = "data-flow-request-queue";
    public static final String DATA_FLOW_PROCESS_QUEUE = "data-flow-process-queue";
    public static final String HEALTH_INFO_QUEUE = "health-info-queue";
    public static final String HIU_DEAD_LETTER_QUEUE = "hiu-dead-letter-queue";
    private static final String HIU_DEAD_LETTER_EXCHANGE = "hiu-dead-letter-exchange";
    public static final String HIU_DEAD_LETTER_ROUTING_KEY = "deadLetter";
    public static final String EXCHANGE = "exchange";

    @Bean
    public PatientServiceClient patientServiceClient(
            WebClient.Builder builder,
            ConsentManagerServiceProperties consentManagerServiceProperties) {
        return new PatientServiceClient(builder, consentManagerServiceProperties);
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
    public DataFlowRequestPublisher dataFlowRequestPublisher(AmqpTemplate amqpTemplate,
                                                             DestinationsConfig destinationsConfig) {
        return new DataFlowRequestPublisher(amqpTemplate, destinationsConfig);
    }

    @Bean
    public HealthInformationPublisher healthInformationDeletionPublisher(AmqpTemplate amqpTemplate,
                                                                         DestinationsConfig destinationsConfig) {
        return new HealthInformationPublisher(amqpTemplate, destinationsConfig);
    }

    @Bean
    public ConsentService consentService(
            WebClient.Builder builder,
            ConsentManagerServiceProperties consentManagerServiceProperties,
            HiuProperties hiuProperties,
            ConsentRepository consentRepository,
            DataFlowRequestPublisher dataFlowRequestPublisher,
            PatientService patientService,
            CentralRegistry centralRegistry,
            HealthInformationPublisher healthInformationPublisher) {
        return new ConsentService(
                new ConsentManagerClient(builder, consentManagerServiceProperties),
                hiuProperties,
                consentRepository,
                dataFlowRequestPublisher,
                patientService,
                centralRegistry,
                healthInformationPublisher);
    }

    @Bean
    public PatientService patientService(PatientServiceClient patientServiceClient,
                                         Cache<String, Optional<Patient>> cache,
                                         CentralRegistry centralRegistry) {
        return new PatientService(patientServiceClient, cache, centralRegistry);
    }

    @Bean
    public Cache<String, Optional<Patient>> cache() {
        return CacheBuilder
                .newBuilder()
                .maximumSize(50)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build(new CacheLoader<>() {
                    public Optional<Patient> load(String key) {
                        return Optional.empty();
                    }
                });
    }

    @Bean
    public ConsentRepository consentRepository(PgPool pgPool) {
        return new ConsentRepository(pgPool);
    }

    @Bean
    // This exception handler needs to be given highest priority compared to DefaultErrorWebExceptionHandler, hence
    // order = -2.
    @Order(-2)
    public ClientErrorExceptionHandler clientErrorExceptionHandler(ErrorAttributes errorAttributes,
                                                                   ResourceProperties resourceProperties,
                                                                   ApplicationContext applicationContext,
                                                                   ServerCodecConfigurer serverCodecConfigurer) {

        ClientErrorExceptionHandler clientErrorExceptionHandler = new ClientErrorExceptionHandler(errorAttributes,
                resourceProperties, applicationContext);
        clientErrorExceptionHandler.setMessageWriters(serverCodecConfigurer.getWriters());
        return clientErrorExceptionHandler;
    }

    @Bean
    public DestinationsConfig destinationsConfig(AmqpAdmin amqpAdmin) {
        HashMap<String, DestinationsConfig.DestinationInfo> queues = new HashMap<>();
        queues.put(DATA_FLOW_REQUEST_QUEUE, new DestinationsConfig.DestinationInfo(EXCHANGE, DATA_FLOW_REQUEST_QUEUE));
        queues.put(DATA_FLOW_PROCESS_QUEUE, new DestinationsConfig.DestinationInfo(EXCHANGE, DATA_FLOW_PROCESS_QUEUE));
        queues.put(HEALTH_INFO_QUEUE, new DestinationsConfig.DestinationInfo(EXCHANGE, HEALTH_INFO_QUEUE));

        DestinationsConfig destinationsConfig = new DestinationsConfig(queues, null);
        Queue deadLetterQueue = QueueBuilder.durable(HIU_DEAD_LETTER_QUEUE).build();
        Binding with = BindingBuilder
                .bind(deadLetterQueue)
                .to(new DirectExchange(HIU_DEAD_LETTER_EXCHANGE))
                .with(HIU_DEAD_LETTER_ROUTING_KEY);
        amqpAdmin.declareQueue(deadLetterQueue);
        amqpAdmin.declareExchange(new DirectExchange(HIU_DEAD_LETTER_EXCHANGE));
        amqpAdmin.declareBinding(with);
        destinationsConfig.getQueues()
                .forEach((key, destination) -> {
                    Exchange ex = ExchangeBuilder.directExchange(
                            destination.getExchange())
                            .durable(true)
                            .build();
                    amqpAdmin.declareExchange(ex);
                    Queue q = QueueBuilder.durable(
                            destination.getRoutingKey())
                            .deadLetterExchange(HIU_DEAD_LETTER_EXCHANGE)
                            .deadLetterRoutingKey(HIU_DEAD_LETTER_ROUTING_KEY)
                            .build();
                    amqpAdmin.declareQueue(q);
                    Binding b = BindingBuilder.bind(q)
                            .to(ex)
                            .with(destination.getRoutingKey())
                            .noargs();
                    amqpAdmin.declareBinding(b);
                });

        return destinationsConfig;
    }

    @Bean
    public Jackson2JsonMessageConverter converter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public MessageListenerContainerFactory messageListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter) {
        return new MessageListenerContainerFactory(connectionFactory, jackson2JsonMessageConverter);
    }

    @Bean
    public DataFlowClient dataFlowClient(WebClient.Builder builder,
                                         ConsentManagerServiceProperties consentManagerServiceProperties) {
        return new DataFlowClient(builder, consentManagerServiceProperties);
    }

    @Bean
    public DataFlowRepository dataFlowRequestRepository(PgPool pgPool) {
        return new DataFlowRepository(pgPool);
    }

    @Bean
    public HealthInformationRepository healthInformationRepository(PgPool pgPool) {
        return new HealthInformationRepository(pgPool);
    }

    @Bean
    public Decryptor decryptor() {
        return new Decryptor();
    }

    @Bean
    public DataFlowRequestListener dataFlowRequestListener(
            MessageListenerContainerFactory messageListenerContainerFactory,
            DestinationsConfig destinationsConfig,
            DataFlowClient dataFlowClient,
            DataFlowRepository dataFlowRepository,
            Decryptor decryptor,
            DataFlowProperties dataFlowProperties,
            CentralRegistry centralRegistry) {
        return new DataFlowRequestListener(
                messageListenerContainerFactory,
                destinationsConfig,
                dataFlowClient,
                dataFlowRepository,
                decryptor,
                dataFlowProperties,
                centralRegistry);
    }

    @Bean
    public LocalDataStore localDataStore() {
        return new LocalDataStore();
    }

    @Bean
    public DataFlowService dataFlowService(DataFlowRepository dataFlowRepository,
                                           DataAvailabilityPublisher dataAvailabilityPublisher,
                                           DataFlowServiceProperties properties,
                                           LocalDataStore localDataStore) {
        return new DataFlowService(
                dataFlowRepository,
                dataAvailabilityPublisher,
                properties,
                localDataStore);
    }

    @Bean
    public HealthInfoManager healthInfoManager(ConsentRepository consentRepository,
                                               DataFlowRepository dataFlowRepository,
                                               HealthInformationRepository healthInformationRepository) {
        return new HealthInfoManager(consentRepository, dataFlowRepository, healthInformationRepository);
    }

    @Bean
    public HealthDataRepository healthDataRepository(PgPool pgPool) {
        return new HealthDataRepository(pgPool);
    }

    @Bean
    public DataAvailabilityPublisher dataAvailabilityPublisher(AmqpTemplate amqpTemplate,
                                                               DestinationsConfig destinationsConfig) {
        return new DataAvailabilityPublisher(amqpTemplate, destinationsConfig);
    }

    @Bean
    public DataAvailabilityListener dataAvailabilityListener(MessageListenerContainerFactory messageListenerContainerFactory,
                                                             DestinationsConfig destinationsConfig,
                                                             HealthDataRepository healthDataRepository,
                                                             DataFlowRepository dataFlowRepository,
                                                             LocalDicomServerProperties dicomServerProperties,
                                                             HealthInformationClient healthInformationClient,
                                                             CentralRegistry centralRegistry,
                                                             HiuProperties hiuProperties,
                                                             ConsentRepository consentRepository) {
        return new DataAvailabilityListener(
                messageListenerContainerFactory,
                destinationsConfig,
                healthDataRepository,
                dataFlowRepository,
                dicomServerProperties,
                healthInformationClient,
                centralRegistry,
                hiuProperties,
                consentRepository);
    }

    @Bean
    public CentralRegistryClient centralRegistryClient(WebClient.Builder builder,
                                                       CentralRegistryProperties centralRegistryProperties) {
        return new CentralRegistryClient(builder.baseUrl(centralRegistryProperties.url));
    }

    @Bean
    public HealthInformationClient healthInformationClient(WebClient.Builder builder,
                                                       ConsentManagerServiceProperties consentManagerServiceProperties) {
        return new HealthInformationClient(builder, consentManagerServiceProperties);
    }
    @Bean
    public CentralRegistry connector(CentralRegistryProperties centralRegistryProperties,
                                     CentralRegistryClient centralRegistryClient) {
        return new CentralRegistry(centralRegistryProperties, centralRegistryClient);
    }

    @Bean("centralRegistryJWKSet")
    public JWKSet jwkSet(CentralRegistryProperties clientRegistryProperties) throws IOException, ParseException {
        return JWKSet.load(new URL(clientRegistryProperties.getJwkUrl()));
    }

    @Bean
    public CentralRegistryTokenVerifier centralRegistryTokenVerifier(@Qualifier("centralRegistryJWKSet") JWKSet jwkSet) {
        return new CentralRegistryTokenVerifier(jwkSet);
    }

    @Bean
    @ConditionalOnProperty(value = "hiu.loginMethod", havingValue = "jwt")
    public Authenticator userAuthenticator(byte[] sharedSecret) throws JOSEException {
        return new UserAuthenticator(sharedSecret);
    }

    @Bean
    @ConditionalOnProperty(value = "hiu.loginMethod", havingValue = "base64")
    public Authenticator base64Authenticator() {
        return new Base64Authenticator();
    }

    @Bean
    @ConditionalOnProperty(value = "hiu.loginMethod", havingValue = "both", matchIfMissing = true)
    public Authenticator userBase64Authenticator(byte[] sharedSecret) throws JOSEException {
        return new UserBase64Authenticator(new Base64Authenticator(), new UserAuthenticator(sharedSecret));
    }

    @Bean
    public SessionService sessionService(BCryptPasswordEncoder bCryptPasswordEncoder,
                                         UserRepository userRepository,
                                         JWTGenerator jwtGenerator) {
        return new SessionService(userRepository, bCryptPasswordEncoder, jwtGenerator);
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserRepository userRepository(PgPool pgPool) {
        return new UserRepository(pgPool);
    }

    @Bean
    public static byte[] sharedSecret() {
        SecureRandom random = new SecureRandom();
        byte[] sharedSecret = new byte[32];
        random.nextBytes(sharedSecret);
        return sharedSecret;
    }

    @Bean
    public JWTGenerator jwt(byte[] sharedSecret) {
        return new JWTGenerator(sharedSecret);
    }
}