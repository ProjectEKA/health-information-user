package in.org.projecteka.hiu.dicomweb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@NoArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DicomStudy {
    @JsonProperty("ID")
    private String studyUuid;
    private String studyInstanceUid;

    //@SuppressWarnings("unchecked")
    @JsonProperty("MainDicomTags")
    private void setStudyInstanceValue(Map<String,Object> dicomTags) {
        this.studyInstanceUid = (String) dicomTags.get("StudyInstanceUID");
    }
}
