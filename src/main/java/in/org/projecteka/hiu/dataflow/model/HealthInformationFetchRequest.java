package in.org.projecteka.hiu.dataflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@AllArgsConstructor
@Data
public class HealthInformationFetchRequest {
    private List<String> requestIds;
    private Integer limit;
    private Integer offset;
}
