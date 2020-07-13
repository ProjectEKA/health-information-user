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

    public Integer getLimit(int defaultLimit){
        return limit == null ? defaultLimit : limit;
    }

    public Integer getOffset(){
        return offset == null ? 0 : offset;
    }
}
