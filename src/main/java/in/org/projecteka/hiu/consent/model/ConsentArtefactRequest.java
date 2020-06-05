package in.org.projecteka.hiu.consent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConsentArtefactRequest {
    private UUID requestId;
    private LocalDateTime timestamp;
    private String consentId;
}
