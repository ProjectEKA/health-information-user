package in.org.projecteka.hiu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ConsentManagerServiceProperties.class})
public class HiuApplication {

	public static void main(String[] args) {
		SpringApplication.run(HiuApplication.class, args);
	}
}