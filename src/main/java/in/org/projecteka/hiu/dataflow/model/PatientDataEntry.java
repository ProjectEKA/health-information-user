package in.org.projecteka.hiu.dataflow.model;

import in.org.projecteka.hiu.dataprocessor.model.EntryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@Builder
@NoArgsConstructor
public class PatientDataEntry {
    private String hipId;
    private String consentRequestId;
    private String consentArtefactId;
    private EntryStatus status;
    private Object data;
    private String docId;
    private String docOriginId;
}
