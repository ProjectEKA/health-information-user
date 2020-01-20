package in.org.projecteka.hiu.consent.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ConsentRequest {
    private String id;
    private String requesterId;
    private Patient patient;
    private Purpose purpose;
    private List<HIType> hiTypes;
    private Permission permission;
    private ConsentStatus status;
    private String createdDate;
}
