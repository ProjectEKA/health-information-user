package in.org.projecteka.hiu.consent.model.consentManager;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.org.projecteka.hiu.consent.model.DateRange;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@Builder
@NoArgsConstructor
@Data
public class Permission {
    private AccessMode accessMode;
    private DateRange dateRange;
    private String dataExpiryAt;
    private Frequency frequency;
}
