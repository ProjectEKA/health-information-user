package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.common.Constants;
import in.org.projecteka.hiu.consent.model.HIType;
import org.hl7.fhir.r4.model.CodeableConcept;

import java.util.HashMap;
import java.util.Map;

public class FHIRUtils {
    public static String getDisplay(CodeableConcept codeableConcept) {
        if (codeableConcept != null && codeableConcept.hasCoding()) {
            return codeableConcept.getCodingFirstRep().getDisplay();
        }
        return Constants.EMPTY_STRING;
    }

    private static Map<String, HIType> docCodes = new HashMap<>() {{
        put("721981007", HIType.DIAGNOSTIC_REPORT);
        put("722124004", HIType.DIAGNOSTIC_REPORT);
        put("4241000179101", HIType.DIAGNOSTIC_REPORT);
        put("371530004", HIType.OP_CONSULTATION);
        put("373942005", HIType.DISCHARGE_SUMMARY);
        put("440545006", HIType.PRESCRIPTION);
    }};

    public static String getHiTypeForCode(String code) {
        HIType hiType = docCodes.get(code);
        return hiType != null ? hiType.getValue() : "";
    }
}
