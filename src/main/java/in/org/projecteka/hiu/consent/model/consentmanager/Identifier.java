package in.org.projecteka.hiu.consent.model.consentmanager;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@Data
public class Identifier {
    private String value;
    private String type;
    private String system;
}