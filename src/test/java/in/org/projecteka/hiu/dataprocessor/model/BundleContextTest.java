package in.org.projecteka.hiu.dataprocessor.model;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Organization;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class BundleContextTest {

    @Test
    void shouldFindOrganizationFromBundle() throws IOException {
        Path filePath = Paths.get("src", "test", "resources", "MAXNCC1543DischargeSummaryDoc20190419.json");
        Stream<String> lines = Files.lines(filePath);
        String content = lines.collect(Collectors.joining());
        IParser jsonParser = FhirContext.forR4().newJsonParser();
        Bundle bundle = jsonParser.parseResource(Bundle.class, content);
        BundleContext bundleContext = new BundleContext(bundle, null);
        List<Organization> organizations = bundleContext.getOrigins();
        //one of the orgs will have a proper domain
        Assert.assertEquals(2, organizations.size());
    }

}