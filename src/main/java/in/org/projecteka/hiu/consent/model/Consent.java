package in.org.projecteka.hiu.consent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.org.projecteka.hiu.consent.ConceptLookup;
import in.org.projecteka.hiu.consent.model.consentmanager.AccessMode;
import in.org.projecteka.hiu.consent.model.consentmanager.HIU;
import in.org.projecteka.hiu.consent.model.consentmanager.Requester;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

import static in.org.projecteka.hiu.consent.model.consentmanager.Frequency.ONE_HOUR;

@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@Builder
@NoArgsConstructor
@Data
public class Consent {
    private Patient patient;
    private Purpose purpose;
    private List<HIType> hiTypes;
    private Permission permission;

    public in.org.projecteka.hiu.consent.model.consentmanager.Consent to(String requesterId,
                                                                         String hiuId,
                                                                         String hiuName,
                                                                         ConceptLookup conceptLookup) {
        return new in.org.projecteka.hiu.consent.model.consentmanager.Consent(
                new in.org.projecteka.hiu.consent.model.consentmanager.Purpose(
                        conceptLookup.getPurposeDescription(getPurpose().getCode()),
                        getPurpose().getCode()),
                getPatient(),
                new HIU(hiuId, hiuName),
                new Requester(requesterId),
                getHiTypes(),
                new in.org.projecteka.hiu.consent.model.consentmanager.Permission(
                        AccessMode.VIEW,
                        getPermission().getDateRange(),
                        getPermission().getDataEraseAt(),
                        ONE_HOUR));
    }

    public ConsentRequest toConsentRequest(String id, String requesterId) {
        return ConsentRequest.builder()
                .id(id)
                .requesterId(requesterId)
                .patient(getPatient())
                .purpose(getPurpose())
                .hiTypes(getHiTypes())
                .permission(getPermission())
                .status(ConsentStatus.REQUESTED)
                .createdDate(LocalDateTime.now())
                .build();
    }

    public in.org.projecteka.hiu.consent.model.consentmanager.Consent to(String requesterId,
                                                                         String hiuId,
                                                                         ConceptLookup conceptLookup) {

        return in.org.projecteka.hiu.consent.model.consentmanager.Consent.builder()
                .purpose(new in.org.projecteka.hiu.consent.model.consentmanager.Purpose(
                        conceptLookup.getPurposeDescription(getPurpose().getCode()),
                        getPurpose().getCode()))
                .patient(getPatient())
                .hiu(HIU.builder().id(hiuId).build())
                .requester(new Requester(requesterId))
                .hiTypes(getHiTypes())
                .permission( new in.org.projecteka.hiu.consent.model.consentmanager.Permission(
                        AccessMode.VIEW,
                        getPermission().getDateRange(),
                        getPermission().getDataEraseAt(),
                        ONE_HOUR))
                .build();

    }
}
