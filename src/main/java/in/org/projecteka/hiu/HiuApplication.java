package in.org.projecteka.hiu;

import in.org.projecteka.hiu.patient.ConsentManagerServiceProperties;
import in.org.projecteka.hiu.patient.PatientServiceClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
@EnableConfigurationProperties(ConsentManagerServiceProperties.class)
public class HiuApplication {

	public static void main(String[] args) {
		SpringApplication.run(HiuApplication.class, args);
	}

	@Bean
	public PatientServiceClient patientServiceClient(WebClient.Builder builder,
													 ConsentManagerServiceProperties consentManagerServiceProperties) {
		return new PatientServiceClient(builder, consentManagerServiceProperties);
	}
}