package in.org.projecteka.hiu.consent.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PatientConsentRequest {
    private List<String> hipIds;
}
