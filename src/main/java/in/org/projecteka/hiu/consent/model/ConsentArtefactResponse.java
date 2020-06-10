package in.org.projecteka.hiu.consent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class ConsentArtefactResponse {
    private ConsentStatus status;
    private ConsentArtefact consentDetail;
    private String signature;
}