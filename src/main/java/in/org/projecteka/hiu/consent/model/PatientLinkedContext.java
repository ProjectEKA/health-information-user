package in.org.projecteka.hiu.consent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class PatientLinkedContext {
    private String id;
    private List<LinkedContext> careContexts;
}
