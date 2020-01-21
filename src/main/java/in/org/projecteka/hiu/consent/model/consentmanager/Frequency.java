package in.org.projecteka.hiu.consent.model.consentmanager;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@Builder
@Data
public class Frequency {
    private Unit unit;
    private int value;
    public static Frequency ZERO_HOUR = new Frequency(Unit.HOUR, 0);
}
