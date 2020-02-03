package in.org.projecteka.hiu.consent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class PatientLinkedContext {
    private String id;
    private List<LinkedContext> careContexts;
}
