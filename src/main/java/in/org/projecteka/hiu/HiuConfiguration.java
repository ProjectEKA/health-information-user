package in.org.projecteka.hiu;

import in.org.projecteka.hiu.consent.ConsentManagerClient;
import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.consent.ConsentService;
import in.org.projecteka.hiu.consent.DataFlowRequestPublisher;
import in.org.projecteka.hiu.dataflow.DataFlowClient;
import in.org.projecteka.hiu.dataflow.DataFlowRequestListener;
import in.org.projecteka.hiu.dataflow.DataFlowRequestRepository;
import in.org.projecteka.hiu.patient.PatientServiceClient;
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
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.web.ResourceProperties;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

import java.util.HashMap;

@Configuration
public class HiuConfiguration {
    public static final String DATA_FLOW_REQUEST_QUEUE = "data-flow-request-queue";

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
    public DataFlowRequestPublisher dataFlowRequestPublisher(AmqpTemplate amqpTemplate, DestinationsConfig destinationsConfig) {
        return new DataFlowRequestPublisher(amqpTemplate, destinationsConfig);
    }

    @Bean
    public ConsentService consentService(
            WebClient.Builder builder,
            ConsentManagerServiceProperties consentManagerServiceProperties,
            HiuProperties hiuProperties,
            ConsentRepository consentRepository,
            DataFlowRequestPublisher dataFlowRequestPublisher) {
        return new ConsentService(
                new ConsentManagerClient(builder, consentManagerServiceProperties, hiuProperties),
                hiuProperties,
                consentRepository,
                dataFlowRequestPublisher);
    }

    @Bean
    public ConsentRepository consentRepository(PgPool pgPool) {
        return new ConsentRepository(pgPool);
    }

    @Bean
    // This exception handler needs to be given highest priority compared to DefaultErrorWebExceptionHandler, hence order = -2.
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
        queues.put(DATA_FLOW_REQUEST_QUEUE, new DestinationsConfig.DestinationInfo("exchange", DATA_FLOW_REQUEST_QUEUE));

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
    public DataFlowRequestRepository dataFlowRequestRepository(PgPool pgPool) {
        return new DataFlowRequestRepository(pgPool);
    }

    @Bean
    public DataFlowRequestListener dataFlowRequestListener(MessageListenerContainerFactory messageListenerContainerFactory,
                                                           DestinationsConfig destinationsConfig,
                                                           DataFlowClient dataFlowClient,
                                                           DataFlowRequestRepository dataFlowRequestRepository) {
        return new DataFlowRequestListener(messageListenerContainerFactory,
                destinationsConfig,
                dataFlowClient, dataFlowRequestRepository);
    }
}