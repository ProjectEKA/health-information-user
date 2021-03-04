package in.org.projecteka.hiu;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Map;
import java.util.UUID;

import static in.org.projecteka.hiu.common.Constants.CORRELATION_ID;

@Component
@Slf4j
public class CorrelationIDFilter implements WebFilter {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var path = exchange.getRequest().getPath().toString();
        var requestMethod = exchange.getRequest().getMethod();

        return chain.filter(exchange)
                .subscriberContext(context -> {
                    String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID);
                    if (StringUtils.isBlank(correlationId)) {
                        correlationId = generateRandomCorrelationId();
                    }
                    MDC.put(CORRELATION_ID, correlationId);
                    if (!path.endsWith("/heartbeat") && !path.endsWith("/readiness")){
                        logger.info("Received a request for path: {}, method: {}", path, requestMethod);
                    }
                    Context contextTmp = context.put(CORRELATION_ID, correlationId);
                    exchange.getAttributes().put(CORRELATION_ID, correlationId);
                    exchange.getResponse().getHeaders().add(CORRELATION_ID, correlationId);
                    return contextTmp;
                });
    }

    private String generateRandomCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
