package in.org.projecteka.hiu.dataflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@Builder
@NoArgsConstructor
@Data
public class PatientHealthInformation {
    private int size;
    private int limit;
    private int offset;
    private List<PatientDataEntry> entries;
}
