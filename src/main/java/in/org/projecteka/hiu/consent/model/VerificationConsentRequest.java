package in.org.projecteka.hiu.consent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor
@Data
@ToString
public class VerificationConsentRequest implements Serializable {
    private String purposeCode;
    private String patientId;
    private String hiuId;
}