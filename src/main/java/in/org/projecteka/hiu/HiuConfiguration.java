package in.org.projecteka.hiu;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import in.org.projecteka.hiu.DestinationsConfig.DestinationInfo;
import in.org.projecteka.hiu.clients.AccountServiceProperties;
import in.org.projecteka.hiu.clients.GatewayAuthenticationClient;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.clients.HealthInformationClient;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.common.Authenticator;
import in.org.projecteka.hiu.common.CMAccountServiceAuthenticator;
import in.org.projecteka.hiu.common.CMPatientAuthenticator;
import in.org.projecteka.hiu.common.CacheMethodProperty;
import in.org.projecteka.hiu.common.Gateway;
import in.org.projecteka.hiu.common.GatewayTokenVerifier;
import in.org.projecteka.hiu.common.RabbitQueueNames;
import in.org.projecteka.hiu.common.RedisOptions;
import in.org.projecteka.hiu.common.UserAuthenticator;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import in.org.projecteka.hiu.common.cache.LoadingCacheGenericAdapter;
import in.org.projecteka.hiu.common.cache.RedisGenericAdapter;
import in.org.projecteka.hiu.common.heartbeat.CacheHealth;
import in.org.projecteka.hiu.common.heartbeat.Heartbeat;
import in.org.projecteka.hiu.common.heartbeat.RabbitMQOptions;
import in.org.projecteka.hiu.consent.ConceptValidator;
import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.consent.ConsentService;
import in.org.projecteka.hiu.consent.ConsentServiceProperties;
import in.org.projecteka.hiu.consent.DataFlowDeletePublisher;
import in.org.projecteka.hiu.consent.DataFlowRequestPublisher;
import in.org.projecteka.hiu.consent.HealthInformationPublisher;
import in.org.projecteka.hiu.consent.PatientConsentRepository;
import in.org.projecteka.hiu.consent.PatientConsentService;
import in.org.projecteka.hiu.dataflow.DataAvailabilityPublisher;
import in.org.projecteka.hiu.dataflow.DataFlowClient;
import in.org.projecteka.hiu.dataflow.DataFlowDeleteListener;
import in.org.projecteka.hiu.dataflow.DataFlowRepository;
import in.org.projecteka.hiu.dataflow.DataFlowRequestListener;
import in.org.projecteka.hiu.dataflow.DataFlowService;
import in.org.projecteka.hiu.dataflow.DataFlowServiceProperties;
import in.org.projecteka.hiu.dataflow.Decryptor;
import in.org.projecteka.hiu.dataflow.HealthInfoManager;
import in.org.projecteka.hiu.dataflow.HealthInformationRepository;
import in.org.projecteka.hiu.dataflow.LocalDataStore;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.PatientHealthInfoStatus;
import in.org.projecteka.hiu.dataprocessor.DataAvailabilityListener;
import in.org.projecteka.hiu.dataprocessor.HealthDataRepository;
import in.org.projecteka.hiu.patient.PatientService;
import in.org.projecteka.hiu.patient.model.PatientSearchGatewayResponse;
import in.org.projecteka.hiu.user.JWTGenerator;
import in.org.projecteka.hiu.user.SessionService;
import in.org.projecteka.hiu.user.SessionServiceClient;
import in.org.projecteka.hiu.user.UserRepository;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ResourceProperties;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.ReactiveRedisClusterConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializationContext.RedisSerializationContextBuilder;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static in.org.projecteka.hiu.common.Constants.EMPTY_STRING;
import static io.lettuce.core.ReadFrom.REPLICA_PREFERRED;
import static java.time.Duration.ofDays;
import static java.time.Duration.ofMinutes;

@Configuration
public class HiuConfiguration {
    private static final String HIU_DEAD_LETTER_EXCHANGE = "hiu-dead-letter-exchange";
    public static final String HIU_DEAD_LETTER_ROUTING_KEY = "deadLetter";
    public static final String EXCHANGE = "exchange";

    @Bean("readWriteClient")
    public PgPool readWriteClient(DatabaseProperties dbProps) {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setPort(dbProps.getPort())
                .setHost(dbProps.getHost())
                .setDatabase(dbProps.getSchema())
                .setUser(dbProps.getUser())
                .setPassword(dbProps.getPassword());

        PoolOptions poolOptions = new PoolOptions().setMaxSize(dbProps.getPoolSize());
        return PgPool.pool(connectOptions, poolOptions);
    }

    @Bean("readOnlyClient")
    public PgPool readOnlyClient(DatabaseProperties dbProps) {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setPort(dbProps.getReplica().getPort())
                .setHost(dbProps.getReplica().getHost())
                .setDatabase(dbProps.getSchema())
                .setUser(dbProps.getReplica().getUser())
                .setPassword(dbProps.getReplica().getPassword());

        PoolOptions poolOptions = new PoolOptions().setMaxSize(dbProps.getReplica().getPoolSize());
        return PgPool.pool(connectOptions, poolOptions);
    }

    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "redis")
    @Bean("Lettuce")
    ReactiveRedisConnectionFactory redisConnection(RedisOptions redisOptions) {
        var hiu = "HIU-Redis-Client";
        var clientOptions = ClientOptions.builder()
                .socketOptions(SocketOptions.builder().keepAlive(redisOptions.isKeepAliveEnabled()).build())
                .build();
        var clientConfiguration = LettuceClientConfiguration.builder()
                .clientName(hiu)
                .clientOptions(clientOptions)
                .readFrom(REPLICA_PREFERRED)
                .build();
        var configuration = new RedisStandaloneConfiguration(redisOptions.getHost(), redisOptions.getPort());
        configuration.setPassword(redisOptions.getPassword());
        return new LettuceConnectionFactory(configuration, clientConfiguration);
    }

    @Bean
    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "guava", matchIfMissing = true)
    public LoadingCache<String, String> loadingCacheForAccessToken() {
        return CacheBuilder
                .newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build(new CacheLoader<>() {
                    public String load(String key) {
                        return EMPTY_STRING;
                    }
                });
    }

    @Bean("accessToken")
    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "guava", matchIfMissing = true)
    public CacheAdapter<String, String> accessTokenCacheAdapter(
            LoadingCache<String, String> loadingCacheForAccessToken) {
        return new LoadingCacheGenericAdapter<>(loadingCacheForAccessToken, EMPTY_STRING);
    }

    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "redis")
    @Bean("accessToken")
    public CacheAdapter<String, String> redisAccessTokenCacheAdapter(
            ReactiveRedisOperations<String, String> stringReactiveRedisOperations,
            RedisOptions redisOptions,
            GatewayProperties gatewayProperties) {
        return new RedisGenericAdapter<>(stringReactiveRedisOperations,
                ofMinutes(gatewayProperties.getAccessTokenExpiryInMinutes()),
                "hiu-gateway-accessToken",
                redisOptions.getRetry());
    }

    @Bean
    public DataFlowRequestPublisher dataFlowRequestPublisher(AmqpTemplate amqpTemplate,
                                                             DestinationsConfig destinationsConfig,
                                                             RabbitQueueNames queueNames) {
        return new DataFlowRequestPublisher(amqpTemplate, destinationsConfig, queueNames);
    }

    @Bean
    public DataFlowDeletePublisher dataFlowDeletePublisher(AmqpTemplate amqpTemplate,
                                                           DestinationsConfig destinationsConfig,
                                                           RabbitQueueNames queueNames) {
        return new DataFlowDeletePublisher(amqpTemplate, destinationsConfig, queueNames);
    }

    @Bean
    public HealthInformationPublisher healthInformationDeletionPublisher(AmqpTemplate amqpTemplate,
                                                                         DestinationsConfig destinationsConfig,
                                                                         RabbitQueueNames queueNames) {
        return new HealthInformationPublisher(amqpTemplate, destinationsConfig, queueNames);
    }

    @Bean
    public ConsentService consentService(
            HiuProperties hiuProperties,
            ConsentRepository consentRepository,
            DataFlowRequestPublisher dataFlowRequestPublisher,
            DataFlowDeletePublisher dataFlowDeletePublisher,
            PatientService patientService,
            HealthInformationPublisher healthInformationPublisher,
            ConceptValidator validator,
            GatewayServiceClient gatewayServiceClient,
            PatientConsentRepository patientConsentRepository,
            ConsentServiceProperties consentServiceProperties,
            @Qualifier("patientRequestCache") CacheAdapter<String, String> patientRequestCache,
            @Qualifier("gatewayResponseCache") CacheAdapter<String, String> gatewayResponseCache) {

        return new ConsentService(
                hiuProperties,
                consentRepository,
                dataFlowRequestPublisher,
                dataFlowDeletePublisher,
                patientService,
                healthInformationPublisher,
                validator,
                gatewayServiceClient,
                patientConsentRepository,
                consentServiceProperties,
                patientRequestCache,
                gatewayResponseCache);
    }

    @Bean
    public PatientConsentService patientConsentService(
            HiuProperties hiuProperties,
            ConsentRepository consentRepository,
            ConceptValidator validator,
            GatewayServiceClient gatewayServiceClient,
            PatientConsentRepository patientConsentRepository,
            ConsentServiceProperties consentServiceProperties,
            @Qualifier("patientRequestCache") CacheAdapter<String, String> patientRequestCache,
            HealthInfoManager healthInfoManager) {

        BiFunction<List<String>, String, Flux<PatientHealthInfoStatus>> healthInfoStatus = healthInfoManager::fetchHealthInformationStatus;
        return new PatientConsentService(
                consentServiceProperties,
                hiuProperties,
                validator,
                patientRequestCache,
                consentRepository,
                patientConsentRepository,
                gatewayServiceClient,
                healthInfoStatus);

    }

    @Bean
    public PatientService patientService(GatewayServiceClient gatewayServiceClient,
                                         CacheAdapter<String, Patient> cache,
                                         HiuProperties hiuProperties,
                                         GatewayProperties gatewayProperties,
                                         CacheAdapter<String, PatientSearchGatewayResponse> patientSearchCache) {
        return new PatientService(
                gatewayServiceClient,
                cache,
                hiuProperties,
                gatewayProperties,
                patientSearchCache);
    }

    @Bean
    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "guava", matchIfMissing = true)
    public LoadingCache<String, Patient> patientCache() {
        return CacheBuilder
                .newBuilder()
                .maximumSize(50)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build(new CacheLoader<>() {
                    public Patient load(String anyKey) {
                        return Patient.empty();
                    }
                });
    }

    @Bean
    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "guava", matchIfMissing = true)
    public CacheAdapter<String, Patient> patientCacheAdapter(LoadingCache<String, Patient> patientCache) {
        return new LoadingCacheGenericAdapter<>(patientCache, Patient.empty());
    }

    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "redis")
    @Bean
    public CacheAdapter<String, Patient> redisPatientGatewayResponse(
            ReactiveRedisOperations<String, Patient> stringReactiveRedisOperations,
            RedisOptions redisOptions) {
        return new RedisGenericAdapter<>(stringReactiveRedisOperations,
                ofDays(1),
                "hiu-patient",
                redisOptions.getRetry());
    }

    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "redis")
    @Bean
    ReactiveRedisOperations<String, Patient> redisPatientOperations(
            ReactiveRedisConnectionFactory factory) {
        var valueSerializer = new Jackson2JsonRedisSerializer<>(Patient.class);
        RedisSerializationContextBuilder<String, Patient> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());
        return new ReactiveRedisTemplate<>(factory, builder.value(valueSerializer).build());
    }

    @Bean
    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "guava", matchIfMissing = true)
    public LoadingCache<String, DataFlowRequestKeyMaterial> dataFlowCache() {
        return CacheBuilder
                .newBuilder()
                .maximumSize(50)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build(new CacheLoader<>() {
                    public DataFlowRequestKeyMaterial load(String anyKey) {
                        return DataFlowRequestKeyMaterial.empty();
                    }
                });
    }

    @Bean
    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "guava", matchIfMissing = true)
    public CacheAdapter<String, DataFlowRequestKeyMaterial> dataFlowCacheAdapter(
            LoadingCache<String, DataFlowRequestKeyMaterial> dataFlowCache) {
        return new LoadingCacheGenericAdapter<>(dataFlowCache, DataFlowRequestKeyMaterial.empty());
    }

    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "redis")
    @Bean
    public CacheAdapter<String, DataFlowRequestKeyMaterial> redisDataFlowAdapter(
            ReactiveRedisOperations<String, DataFlowRequestKeyMaterial> stringReactiveRedisOperations,
            RedisOptions redisOptions) {
        return new RedisGenericAdapter<>(stringReactiveRedisOperations,
                ofMinutes(30),
                "hiu-data-flow-key",
                redisOptions.getRetry());
    }

    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "redis")
    @Bean
    ReactiveRedisOperations<String, DataFlowRequestKeyMaterial> dataFlowReactiveOperations(
            ReactiveRedisConnectionFactory factory) {
        var valueSerializer = new Jackson2JsonRedisSerializer<>(DataFlowRequestKeyMaterial.class);
        RedisSerializationContextBuilder<String, DataFlowRequestKeyMaterial> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());
        return new ReactiveRedisTemplate<>(factory, builder.value(valueSerializer).build());
    }

    @Bean
    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "guava", matchIfMissing = true)
    public LoadingCache<String, String> stringStringLoadingCache() {
        return CacheBuilder
                .newBuilder()
                .maximumSize(50)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build(new CacheLoader<>() {
                    public String load(String key) {
                        return EMPTY_STRING;
                    }
                });
    }

    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "redis")
    @Bean("gatewayResponseCache")
    public CacheAdapter<String, String> redisGatewayResponseAdapter(
            ReactiveRedisOperations<String, String> stringReactiveRedisOperations,
            RedisOptions redisOptions) {
        return new RedisGenericAdapter<>(stringReactiveRedisOperations,
                ofMinutes(10),
                "hiu-gateway-response",
                redisOptions.getRetry());
    }

    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "redis")
    @Bean("patientRequestCache")
    public CacheAdapter<String, String> redisPatientRequestAdapter(
            ReactiveRedisOperations<String, String> stringReactiveRedisOperations) {
        return new RedisGenericAdapter<>(stringReactiveRedisOperations, ofMinutes(10), "hiu-patient-request");
    }

    @Bean({"gatewayResponseCache", "patientRequestCache"})
    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "guava", matchIfMissing = true)
    public CacheAdapter<String, String> patientRequestCacheAdapter(
            LoadingCache<String, String> stringStringLoadingCache) {
        return new LoadingCacheGenericAdapter<>(stringStringLoadingCache, EMPTY_STRING);
    }

    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "redis")
    @Bean
    ReactiveRedisOperations<String, String> stringReactiveRedisOperations(ReactiveRedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<String> valueSerializer = new Jackson2JsonRedisSerializer<>(String.class);
        RedisSerializationContextBuilder<String, String> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());
        return new ReactiveRedisTemplate<>(factory, builder.value(valueSerializer).build());
    }

    @Bean
    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "guava", matchIfMissing = true)
    public CacheAdapter<String, PatientSearchGatewayResponse> patientSearchCacheAdapter(
            LoadingCache<String, PatientSearchGatewayResponse> patientSearchCache) {
        return new LoadingCacheGenericAdapter<>(patientSearchCache, PatientSearchGatewayResponse.empty());
    }

    @Bean
    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "guava", matchIfMissing = true)
    public LoadingCache<String, PatientSearchGatewayResponse> patientSearchCache() {
        return CacheBuilder
                .newBuilder()
                .maximumSize(50)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build(new CacheLoader<>() {
                    public PatientSearchGatewayResponse load(String anyKey) {
                        return PatientSearchGatewayResponse.empty();
                    }
                });
    }

    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "redis")
    @Bean
    public CacheAdapter<String, PatientSearchGatewayResponse> redisPatientSearchResponse(
            ReactiveRedisOperations<String, PatientSearchGatewayResponse> stringReactiveRedisOperations,
            RedisOptions redisOptions) {
        return new RedisGenericAdapter<>(stringReactiveRedisOperations,
                ofMinutes(30),
                "hiu-patient-gateway-response",
                redisOptions.getRetry());
    }

    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "redis")
    @Bean
    ReactiveRedisOperations<String, PatientSearchGatewayResponse> patientResponseReactiveOperations(
            ReactiveRedisConnectionFactory factory) {
        var valueSerializer = new Jackson2JsonRedisSerializer<>(PatientSearchGatewayResponse.class);
        RedisSerializationContextBuilder<String, PatientSearchGatewayResponse> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());
        return new ReactiveRedisTemplate<>(factory, builder.value(valueSerializer).build());
    }

    @Bean
    public ConsentRepository consentRepository(@Qualifier("readWriteClient") PgPool readWriteClient,
                                               @Qualifier("readOnlyClient") PgPool readOnlyClient) {
        return new ConsentRepository(readWriteClient, readOnlyClient);
    }

    @Bean
    public PatientConsentRepository patientConsentRequestRepository(@Qualifier("readWriteClient") PgPool readWriteClient,
                                                                    @Qualifier("readOnlyClient") PgPool readOnlyClient) {
        return new PatientConsentRepository(readWriteClient, readOnlyClient);
    }

    @Bean
    // This exception handler needs to be given highest priority compared to DefaultErrorWebExceptionHandler, hence
    // order = -2.
    @Order(-2)
    public GlobalExceptionHandler clientErrorExceptionHandler(ErrorAttributes errorAttributes,
                                                              ResourceProperties resourceProperties,
                                                              ApplicationContext applicationContext,
                                                              ServerCodecConfigurer serverCodecConfigurer) {

        GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler(errorAttributes,
                resourceProperties, applicationContext);
        globalExceptionHandler.setMessageWriters(serverCodecConfigurer.getWriters());
        return globalExceptionHandler;
    }

    @Bean
    public RabbitQueueNames queueNames(RabbitMQOptions rabbitMQOptions) {
        return new RabbitQueueNames(rabbitMQOptions.getQueuePrefix());
    }

    @Bean
    public DestinationsConfig destinationsConfig(AmqpAdmin amqpAdmin, RabbitQueueNames queueNames) {
        HashMap<String, DestinationInfo> queues = new HashMap<>();
        queues.put(queueNames.getDataFlowRequestQueue(),
                new DestinationInfo(EXCHANGE, queueNames.getDataFlowRequestQueue()));
        queues.put(queueNames.getDataFlowProcessQueue(),
                new DestinationInfo(EXCHANGE, queueNames.getDataFlowProcessQueue()));
        queues.put(queueNames.getDataFlowDeleteQueue(),
                new DestinationInfo(EXCHANGE, queueNames.getDataFlowDeleteQueue()));
        queues.put(queueNames.getHealthInfoQueue(),
                new DestinationInfo(EXCHANGE, queueNames.getHealthInfoQueue()));

        DestinationsConfig destinationsConfig = new DestinationsConfig(queues, null);
        Queue deadLetterQueue = QueueBuilder.durable(queueNames.getHIUDeadLetterQueue()).build();
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
        var objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public MessageListenerContainerFactory messageListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter) {
        return new MessageListenerContainerFactory(connectionFactory, jackson2JsonMessageConverter);
    }

    @Bean
    public DataFlowClient dataFlowClient(@Qualifier("customBuilder") WebClient.Builder builder,
                                         GatewayProperties gatewayProperties) {
        return new DataFlowClient(builder, gatewayProperties);
    }

    @Bean
    public DataFlowRepository dataFlowRequestRepository(@Qualifier("readWriteClient") PgPool readWriteClient,
                                                        @Qualifier("readOnlyClient") PgPool readOnlyClient) {
        return new DataFlowRepository(readWriteClient, readOnlyClient);
    }

    @Bean
    public HealthInformationRepository healthInformationRepository(@Qualifier("readWriteClient") PgPool readWriteClient,
                                                                   @Qualifier("readOnlyClient") PgPool readOnlyClient) {
        return new HealthInformationRepository(readWriteClient, readOnlyClient);
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
            Gateway gateway,
            CacheAdapter<String, DataFlowRequestKeyMaterial> dataFlowCache,
            ConsentRepository consentRepository,
            RabbitQueueNames queueNames) {
        return new DataFlowRequestListener(
                messageListenerContainerFactory,
                destinationsConfig,
                dataFlowClient,
                dataFlowRepository,
                decryptor,
                dataFlowProperties,
                gateway,
                dataFlowCache,
                consentRepository,
                queueNames);
    }

    @Bean
    public DataFlowDeleteListener dataFlowDeleteListener(
            MessageListenerContainerFactory messageListenerContainerFactory,
            DestinationsConfig destinationsConfig,
            DataFlowRepository dataFlowRepository,
            HealthInformationRepository healthInformationRepository,
            DataFlowServiceProperties dataFlowServiceProperties,
            LocalDataStore localDataStore,
            RabbitQueueNames queueNames) {
        return new DataFlowDeleteListener(
                messageListenerContainerFactory,
                destinationsConfig,
                dataFlowRepository,
                healthInformationRepository,
                dataFlowServiceProperties,
                localDataStore,
                queueNames);
    }

    @Bean
    public LocalDataStore localDataStore() {
        return new LocalDataStore();
    }

    @Bean
    public DataFlowService dataFlowService(DataFlowRepository dataFlowRepository,
                                           DataAvailabilityPublisher dataAvailabilityPublisher,
                                           DataFlowServiceProperties properties,
                                           LocalDataStore localDataStore,
                                           CacheAdapter<String, DataFlowRequestKeyMaterial> dataFlowCache) {
        return new DataFlowService(
                dataFlowRepository,
                dataAvailabilityPublisher,
                properties,
                localDataStore,
                dataFlowCache);
    }

    @Bean
    public HealthInfoManager healthInfoManager(ConsentRepository consentRepository,
                                               DataFlowRepository dataFlowRepository,
                                               HealthInformationRepository healthInformationRepository,
                                               PatientConsentRepository patientConsentRepository,
                                               DataFlowServiceProperties serviceProperties) {
        return new HealthInfoManager(consentRepository,
                dataFlowRepository,
                patientConsentRepository,
                healthInformationRepository,
                serviceProperties);
    }

    @Bean
    public HealthDataRepository healthDataRepository(@Qualifier("readWriteClient") PgPool readWriteClient) {
        return new HealthDataRepository(readWriteClient);
    }

    @Bean
    public DataAvailabilityPublisher dataAvailabilityPublisher(AmqpTemplate amqpTemplate,
                                                               DestinationsConfig destinationsConfig,
                                                               RabbitQueueNames queueNames) {
        return new DataAvailabilityPublisher(amqpTemplate, destinationsConfig, queueNames);
    }

    @Bean
    public DataAvailabilityListener dataAvailabilityListener(
            MessageListenerContainerFactory messageListenerContainerFactory,
            DestinationsConfig destinationsConfig,
            HealthDataRepository healthDataRepository,
            DataFlowRepository dataFlowRepository,
            LocalDicomServerProperties dicomServerProperties,
            HealthInformationClient healthInformationClient,
            Gateway gateway,
            HiuProperties hiuProperties,
            ConsentRepository consentRepository,
            RabbitQueueNames queueNames) {
        return new DataAvailabilityListener(
                messageListenerContainerFactory,
                destinationsConfig,
                healthDataRepository,
                dataFlowRepository,
                dicomServerProperties,
                healthInformationClient,
                gateway,
                hiuProperties,
                consentRepository,
                queueNames);
    }

    @Bean
    public GatewayAuthenticationClient centralRegistryClient(
            @Qualifier("customBuilder") WebClient.Builder builder,
            GatewayProperties gatewayProperties) {
        return new GatewayAuthenticationClient(builder, gatewayProperties.getBaseUrl());
    }

    @Bean
    public HealthInformationClient healthInformationClient(@Qualifier("customBuilder") WebClient.Builder builder,
                                                           GatewayProperties gatewayProperties) {
        return new HealthInformationClient(builder, gatewayProperties);
    }

    @Bean
    public Gateway connector(GatewayProperties gatewayProperties,
                             GatewayAuthenticationClient gatewayAuthenticationClient,
                             @Qualifier("accessToken") CacheAdapter<String, String> accessToken) {
        return new Gateway(gatewayProperties, gatewayAuthenticationClient, accessToken);
    }

    @Bean("centralRegistryJWKSet")
    public JWKSet centralRegistryJWKSet(GatewayProperties gatewayProperties)
            throws IOException, ParseException {
        return JWKSet.load(new URL(gatewayProperties.getJwkUrl()));
    }

    @Bean("identityServiceJWKSet")
    public JWKSet identityServiceJWKSet(IdentityServiceProperties identityServiceProperties)
            throws IOException, ParseException {
        return JWKSet.load(new URL(identityServiceProperties.getJwkUrl()));
    }

    @Bean
    public GatewayTokenVerifier centralRegistryTokenVerifier(@Qualifier("centralRegistryJWKSet") JWKSet jwkSet) {
        return new GatewayTokenVerifier(jwkSet);
    }

    @Bean("hiuUserAuthenticator")
    public Authenticator hiuUserAuthenticator(byte[] sharedSecret) throws JOSEException {
        return new UserAuthenticator(sharedSecret);
    }

    @Bean({"jwtProcessor"})
    public ConfigurableJWTProcessor<SecurityContext> getJWTProcessor() {
        return new DefaultJWTProcessor<>();
    }

    @ConditionalOnProperty(value = "hiu.loginMethod", havingValue = "keycloak", matchIfMissing = true)
    @Bean("userAuthenticator")
    public Authenticator userAuthenticator(@Qualifier("identityServiceJWKSet") JWKSet jwkSet,
                                           ConfigurableJWTProcessor<SecurityContext> jwtProcessor) {
        return new CMPatientAuthenticator(jwkSet, jwtProcessor);
    }

    @ConditionalOnProperty(value = "hiu.loginMethod", havingValue = "service")
    @Bean("userAuthenticator")
    public Authenticator cmAccountServiceTokenAuthenticator(SessionServiceClient sessionServiceClient) {
        return new CMAccountServiceAuthenticator(sessionServiceClient);
    }

    @Bean
    public ReactorClientHttpConnector reactorClientHttpConnector() {
        HttpClient httpClient = null;
        try {
            SslContext sslContext = SslContextBuilder
                    .forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            httpClient = HttpClient.create().secure(t -> t.sslContext(sslContext));
        } catch (SSLException e) {
            e.printStackTrace();
        }
        return new ReactorClientHttpConnector(Objects.requireNonNull(httpClient));
    }

    @Bean
    public SessionServiceClient sessionServiceClient(@Qualifier("customBuilder") WebClient.Builder builder,
                                                     AccountServiceProperties accountServiceProperties) {
        if (accountServiceProperties.isUsingUnsecureSSL()) {
            builder.clientConnector(reactorClientHttpConnector());
        }
        return new SessionServiceClient(builder, accountServiceProperties.getUrl());
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
    public UserRepository userRepository(@Qualifier("readWriteClient") PgPool readWriteClient,
                                         @Qualifier("readOnlyClient") PgPool readOnlyClient) {
        return new UserRepository(readWriteClient, readOnlyClient);
    }

    @Bean
    byte[] sharedSecret(@Value("${hiu.secret}") String secret) {
        return secret.getBytes();
    }

    @Bean
    public JWTGenerator jwt(byte[] sharedSecret) {
        return new JWTGenerator(sharedSecret);
    }

    @Bean
    public GatewayServiceClient gatewayServiceClient(@Qualifier("customBuilder") WebClient.Builder builder,
                                                     GatewayProperties serviceProperties,
                                                     Gateway gateway) {
        return new GatewayServiceClient(builder, serviceProperties, gateway);
    }

    @ConditionalOnProperty(value = "hiu.cache-method", havingValue = "guava", matchIfMissing = true)
    @Bean("Lettuce")
    ReactiveRedisConnectionFactory dummyRedisConnection() {
        return new ReactiveRedisConnectionFactory() {
            @Override
            public ReactiveRedisConnection getReactiveConnection() {
                return null;
            }

            @Override
            public ReactiveRedisClusterConnection getReactiveClusterConnection() {
                return null;
            }

            @Override
            public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
                return null;
            }
        };
    }

    @Bean
    public CacheHealth cacheHealth(@Qualifier("Lettuce") ReactiveRedisConnectionFactory redisConnectionFactory,
                                   CacheMethodProperty cacheMethodProperty) {
        return new CacheHealth(cacheMethodProperty, redisConnectionFactory);
    }

    @Bean
    public Heartbeat heartbeat(RabbitMQOptions rabbitMQOptions,
                               DatabaseProperties databaseProperties,
                               CacheHealth cacheHealth) {
        return new Heartbeat(rabbitMQOptions, databaseProperties, cacheHealth);
    }

    @Bean("hiuHttpConnector")
    @ConditionalOnProperty(value = "webclient.use-connection-pool", havingValue = "false")
    public ClientHttpConnector clientHttpConnector() {
        return new ReactorClientHttpConnector(HttpClient.create(ConnectionProvider.newConnection()));
    }

    @Bean("hiuHttpConnector")
    @ConditionalOnProperty(value = "webclient.use-connection-pool", havingValue = "true")
    public ClientHttpConnector pooledClientHttpConnector(WebClientOptions webClientOptions) {
        return new ReactorClientHttpConnector(
                HttpClient.create(
                        ConnectionProvider.builder("hiu-http-connection-pool")
                                .maxConnections(webClientOptions.getPoolSize())
                                .maxLifeTime(Duration.ofMinutes(webClientOptions.getMaxLifeTime()))
                                .maxIdleTime(Duration.ofMinutes(webClientOptions.getMaxIdleTimeout()))
                                .build()
                )
        );
    }

    @Bean("customBuilder")
    public WebClient.Builder webClient(
            @Qualifier("hiuHttpConnector") final ClientHttpConnector clientHttpConnector,
            ObjectMapper objectMapper) {
        return WebClient
                .builder()
                .exchangeStrategies(exchangeStrategies(objectMapper))
                .clientConnector(clientHttpConnector);
    }

    private ExchangeStrategies exchangeStrategies(ObjectMapper objectMapper) {
        var encoder = new Jackson2JsonEncoder(objectMapper);
        var decoder = new Jackson2JsonDecoder(objectMapper);
        return ExchangeStrategies
                .builder()
                .codecs(configurer -> {
                    configurer.defaultCodecs().jackson2JsonEncoder(encoder);
                    configurer.defaultCodecs().jackson2JsonDecoder(decoder);
                }).build();
    }

    @Bean
    @ConditionalOnProperty(value = "hiu.disableHttpOptionsMethod", havingValue = "true")
    public WebFilter disableOptionsMethodFilter(){
        return (exchange, chain) -> {
            if(exchange.getRequest().getMethod().equals(HttpMethod.OPTIONS)) {
                exchange.getResponse().setStatusCode(HttpStatus.METHOD_NOT_ALLOWED);
                return Mono.empty();
            }
            return chain.filter(exchange);
        };
    }
}
