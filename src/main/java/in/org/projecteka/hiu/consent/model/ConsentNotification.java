package in.org.projecteka.hiu.consent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public class ConsentNotification {
    private String consentRequestId;
    @NotNull
    private ConsentStatus status;
    private List<ConsentArtefactReference> consentArtefacts;
}
