package in.org.projecteka.hiu;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.consent.ConsentManagerClient;
import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.consent.ConsentService;
import in.org.projecteka.hiu.consent.DataFlowRequestPublisher;
import in.org.projecteka.hiu.dataflow.DataAvailabilityPublisher;
import in.org.projecteka.hiu.dataflow.DataFlowClient;
import in.org.projecteka.hiu.dataflow.DataFlowRepository;
import in.org.projecteka.hiu.dataflow.DataFlowRequestListener;
import in.org.projecteka.hiu.dataflow.DataFlowService;
import in.org.projecteka.hiu.dataflow.DataFlowServiceProperties;
import in.org.projecteka.hiu.dataflow.HealthInformationRepository;
import in.org.projecteka.hiu.clients.PatientServiceClient;
import in.org.projecteka.hiu.dataprocessor.DataAvailabilityListener;
import in.org.projecteka.hiu.patient.PatientService;

import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.web.ResourceProperties;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Configuration
public class HiuConfiguration {
    public static final String DATA_FLOW_REQUEST_QUEUE = "data-flow-request-queue";
    public static final String DATA_FLOW_PROCESS_QUEUE = "data-flow-process-queue";

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
    public DataFlowRequestPublisher dataFlowRequestPublisher(AmqpTemplate amqpTemplate,
                                                             DestinationsConfig destinationsConfig) {
        return new DataFlowRequestPublisher(amqpTemplate, destinationsConfig);
    }

    @Bean
    public ConsentService consentService(
            WebClient.Builder builder,
            ConsentManagerServiceProperties consentManagerServiceProperties,
            HiuProperties hiuProperties,
            ConsentRepository consentRepository,
            DataFlowRequestPublisher dataFlowRequestPublisher,
            PatientService patientService) {
        return new ConsentService(
                new ConsentManagerClient(builder, consentManagerServiceProperties, hiuProperties),
                hiuProperties,
                consentRepository,
                dataFlowRequestPublisher,
                patientService);
    }

    @Bean
    public PatientService patientService(PatientServiceClient patientServiceClient,
                                         Cache<String, Optional<Patient>> cache) {
        return new PatientService(patientServiceClient, cache);
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
        queues.put(DATA_FLOW_REQUEST_QUEUE, new DestinationsConfig.DestinationInfo("exchange",
                DATA_FLOW_REQUEST_QUEUE));
        queues.put(DATA_FLOW_PROCESS_QUEUE, new DestinationsConfig.DestinationInfo("exchange",
                DATA_FLOW_PROCESS_QUEUE));

        DestinationsConfig destinationsConfig = new DestinationsConfig(queues, null);
        destinationsConfig.getQueues()
                .forEach((key, destination) -> {
                    Exchange ex = ExchangeBuilder.directExchange(
                            destination.getExchange())
                            .durable(true)
                            .build();
                    amqpAdmin.declareExchange(ex);
                    Queue q = QueueBuilder.durable(
                            destination.getRoutingKey())
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
    public MessageListenerContainerFactory messageListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                           Jackson2JsonMessageConverter jackson2JsonMessageConverter) {
        return new MessageListenerContainerFactory(connectionFactory, jackson2JsonMessageConverter);
    }

    @Bean
    public DataFlowClient dataFlowClient(WebClient.Builder builder,
                                         HiuProperties hiuProperties,
                                         ConsentManagerServiceProperties consentManagerServiceProperties) {
        return new DataFlowClient(builder, hiuProperties, consentManagerServiceProperties);
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
    public DataFlowRequestListener dataFlowRequestListener(MessageListenerContainerFactory messageListenerContainerFactory,
                                                           DestinationsConfig destinationsConfig,
                                                           DataFlowClient dataFlowClient,
                                                           DataFlowRepository dataFlowRepository) {
        return new DataFlowRequestListener(messageListenerContainerFactory,
                destinationsConfig,
                dataFlowClient, dataFlowRepository);
    }

    @Bean
    public DataFlowService dataFlowService(DataFlowRepository dataFlowRepository,
                                           HealthInformationRepository healthInformationRepository,
                                           ConsentRepository consentRepository,
                                           DataAvailabilityPublisher dataAvailabilityPublisher,
                                           DataFlowServiceProperties properties) {
        return new DataFlowService(dataFlowRepository, healthInformationRepository, consentRepository, dataAvailabilityPublisher, properties);
    }

    @Bean
    public DataAvailabilityPublisher dataAvailabilityPublisher(AmqpTemplate amqpTemplate,
                                                              DestinationsConfig destinationsConfig) {
        return new DataAvailabilityPublisher(amqpTemplate, destinationsConfig);
    }

    @Bean
    public DataAvailabilityListener dataAvailabilityListener(MessageListenerContainerFactory messageListenerContainerFactory,
                                                             DestinationsConfig destinationsConfig) {
        return new DataAvailabilityListener(messageListenerContainerFactory, destinationsConfig);
    }
}