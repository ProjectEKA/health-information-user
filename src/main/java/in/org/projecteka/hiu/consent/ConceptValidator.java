package in.org.projecteka.hiu.consent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.org.projecteka.hiu.valuseset.ValueSetResource;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

@Component
public class ConceptValidator implements InitializingBean, ConceptLookup {
    private static final Logger logger = LoggerFactory.getLogger(ConceptValidator.class);

    private ValueSetResource valueSetResource;
    private Map<String, String> purposesOfUse;
    private Map<String, String> hiTypes;

    public ConceptValidator(ValueSetResource valueSetResource) {
        this.valueSetResource = valueSetResource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ConcurrentHashMap<String, Map<String, String>> vsCodeMap = readValueSetFromResource(valueSetResource.getValueSetDefinition());
        purposesOfUse = vsCodeMap.get(HEALTH_INFORMATION_PURPOSE_OF_USE);
        hiTypes = vsCodeMap.get(HEALTH_INFORMATION_TYPE);
    }


    private ConcurrentHashMap<String, Map<String, String>> readValueSetFromResource(String valueSetDef) throws JsonProcessingException {
        try {
            var vsMap = new ConcurrentHashMap<String, Map<String, String>>();
            var mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .configure(WRITE_DATES_AS_TIMESTAMPS, false);
            ArrayNode valuesetNodes = mapper.readValue(valueSetDef, ArrayNode.class);
            for (JsonNode vsNode : valuesetNodes) {
                vsMap.put(vsNode.get("id").asText(), readValueSet(vsNode));
            }
            return vsMap;
        } catch (JsonProcessingException e) {
            logger.error("Error occurred while loading processing Valueset", e);
            throw e;
        }
    }

    private ConcurrentHashMap<String, String> readValueSet(JsonNode vsNode) {
        ConcurrentHashMap<String, String> conceptCodes = new ConcurrentHashMap<>();
        JsonNode compose = vsNode.get("compose");
        if (compose != null && compose.has("include")) {
            ArrayNode includedCodes = compose.withArray("include");
            for (JsonNode includedCode : includedCodes) {
                ArrayNode concepts = includedCode.withArray("concept");
                for (JsonNode concept : concepts) {
                    conceptCodes.put(concept.get("code").asText(), concept.get("display").asText());
                }
            }
        }
        JsonNode expansion = vsNode.get("expansion");
        if (expansion != null && expansion.has("contains")) {
            ArrayNode expandedCodes = expansion.withArray("contains");
            for (JsonNode concept : expandedCodes) {
                conceptCodes.put(concept.get("code").asText(), concept.get("display").asText());
            }
        }
        return conceptCodes;
    }

    public Mono<Boolean> validatePurpose(String code) {
        return Mono.just(purposesOfUse.get(code) != null);
    }


    public Mono<Boolean> validateHITypes(List<String> codes) {
        if (codes.isEmpty()) {
            return Mono.just(false);
        }
        for (String code : codes) {
            if (hiTypes.get(code) == null) {
                return Mono.just(false);
            }
        }
        return Mono.just(true);
    }

    @Override
    public String getDescription(String resourceType, String code) {
        if (resourceType.equalsIgnoreCase(HEALTH_INFORMATION_PURPOSE_OF_USE)) {
            return purposesOfUse.get(code);
        }
        if (resourceType.equalsIgnoreCase(HEALTH_INFORMATION_PURPOSE_OF_USE)) {
            return hiTypes.get(code);
        }
        return null;
    }

    public List<String> getHITypeCodes(){
        return new ArrayList<>(hiTypes.keySet());
    }
}
