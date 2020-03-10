package in.org.projecteka.hiu.dicomweb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DicomInstance {
    @JsonProperty("ID")
    private String id;
    @JsonProperty("ParentPatient")
    private String patientUuid;
    @JsonProperty("ParentSeries")
    private String seriesUuid;
    @JsonProperty("ParentStudy")
    private String studyUuid;
    @JsonProperty("Status")
    private String status;
    @JsonProperty("Path")
    private String path;
}
