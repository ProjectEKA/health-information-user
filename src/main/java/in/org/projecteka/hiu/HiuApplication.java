package in.org.projecteka.hiu;

import in.org.projecteka.hiu.common.CacheMethodProperty;
import in.org.projecteka.hiu.common.RedisOptions;
import in.org.projecteka.hiu.clients.AccountServiceProperties;
import in.org.projecteka.hiu.common.heartbeat.RabbitMQOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = {RedisAutoConfiguration.class})
@EnableConfigurationProperties({ConsentManagerServiceProperties.class,
                                RabbitMQOptions.class,
                                CacheMethodProperty.class,
                                RedisOptions.class,
		                        AccountServiceProperties.class})
public class HiuApplication {

    public static void main(String[] args) {
        SpringApplication.run(HiuApplication.class, args);
    }
}