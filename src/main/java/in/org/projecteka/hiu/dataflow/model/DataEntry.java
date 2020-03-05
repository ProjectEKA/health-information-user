package in.org.projecteka.hiu.dataflow.model;

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
    private Status status;
    private Object data;
}
