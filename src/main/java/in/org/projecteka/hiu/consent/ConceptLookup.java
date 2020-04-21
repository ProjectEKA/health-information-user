package in.org.projecteka.hiu.consent;

public interface ConceptLookup {
    String HEALTH_INFORMATION_PURPOSE_OF_USE = "health-information-purpose-of-use";
    String HEALTH_INFORMATION_TYPE = "health-information-type";
    default String getPurposeDescription(String code) {
        return getDescription(HEALTH_INFORMATION_PURPOSE_OF_USE, code);
    }
    default String getHITypeDescription(String code) {
        return getDescription(HEALTH_INFORMATION_TYPE, code);
    }
    String getDescription(String resourceType, String code);
}
