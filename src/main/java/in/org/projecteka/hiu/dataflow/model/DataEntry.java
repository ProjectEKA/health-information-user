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
public class DataEntry {
    private String hipId;
    private String hipName;
    private EntryStatus status;
    private Object data;
}
