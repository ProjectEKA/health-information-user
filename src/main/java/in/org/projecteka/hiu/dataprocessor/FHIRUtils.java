package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.common.Constants;
import org.hl7.fhir.r4.model.CodeableConcept;

public class FHIRUtils {
    public static String getDisplay(CodeableConcept codeableConcept) {
        if (codeableConcept != null && codeableConcept.hasCoding()) {
            return codeableConcept.getCodingFirstRep().getDisplay();
        }
        return Constants.EMPTY_STRING;
    }
}
