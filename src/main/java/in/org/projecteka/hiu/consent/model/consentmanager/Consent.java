package in.org.projecteka.hiu.consent.model.consentmanager;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.org.projecteka.hiu.consent.model.HIType;
import in.org.projecteka.hiu.consent.model.Patient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@Builder
@NoArgsConstructor
@Data
public class Consent {
    private Purpose purpose;
    private Patient patient;
    private HIU hiu;
    private Requester requester;
    private List<HIType> hiTypes;
    private Permission permission;
    @Valid
    private HIP hip;
}
