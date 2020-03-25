package in.org.projecteka.hiu.clients;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HealthInformation {
    private String content;
    private String transactionId;
}
