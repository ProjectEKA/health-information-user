package in.org.projecteka.hiu.consent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class ConsentRequest {
    private String id;
    private String requesterId;
    private Patient patient;
    private Purpose purpose;
    private List<HIType> hiTypes;
    private Permission permission;
    private ConsentStatus status;
    private Date createdDate;
    private String consentNotificationUrl;
}
