package in.org.projecteka.hiu.consent.model.dataflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HIDataRange {
    private String from;
    private String to;
}
