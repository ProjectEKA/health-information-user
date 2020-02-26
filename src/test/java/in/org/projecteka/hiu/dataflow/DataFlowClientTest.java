package in.org.projecteka.hiu.dataflow;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.ConsentManagerServiceProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestResponse;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.Entry;
import in.org.projecteka.hiu.dataflow.model.HIDataRange;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static in.org.projecteka.hiu.dataflow.TestBuilders.dataFlowRequest;
import static in.org.projecteka.hiu.dataflow.TestBuilders.entry;
import static in.org.projecteka.hiu.dataflow.Utils.toDate;
import static org.assertj.core.api.Assertions.assertThat;

public class DataFlowClientTest {
    private DataFlowClient dataFlowClient;
    private MockWebServer mockWebServer;

    @BeforeEach
    public void init() {
        mockWebServer = new MockWebServer();
        WebClient.Builder webClientBuilder = WebClient.builder();
        ConsentManagerServiceProperties consentManagerServiceProperties =
                new ConsentManagerServiceProperties(mockWebServer.url("").toString());
        HiuProperties hiuProperties = new HiuProperties("10000005", "Max Health Care", "localhost:8080");
        dataFlowClient = new DataFlowClient(webClientBuilder, hiuProperties, consentManagerServiceProperties);

    }

    @Test
    public void shouldCreateConsentRequest() throws JsonProcessingException, InterruptedException, ParseException {
        String transactionId = "transactionId";
        DataFlowRequestResponse dataFlowRequestResponse =
                DataFlowRequestResponse.builder().transactionId(transactionId).build();
        var dataFlowRequestResponseJson = new ObjectMapper().writeValueAsString(dataFlowRequestResponse);
        DataFlowRequest dataFlowRequest = dataFlowRequest().build();
        dataFlowRequest.setHiDataRange(HIDataRange.builder().from(toDate("2020-01-14T08:47:48Z")).to(toDate("2020" +
                "-01-20T08:47:48Z")).build());

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(dataFlowRequestResponseJson));


        StepVerifier.create(dataFlowClient.initiateDataFlowRequest(dataFlowRequest))
                .assertNext(
                        response -> {
                            assertThat(response.getTransactionId()).isEqualTo(transactionId);
                        })
                .verifyComplete();

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getRequestUrl().toString()).isEqualTo(mockWebServer.url("") + "health-information" +
                "/request");
        assertThat(recordedRequest.getBody().readUtf8())
                .isEqualTo(new ObjectMapper().writeValueAsString(dataFlowRequest));
    }

    @Test
    public void shouldTestFHIRResourceParsing() {
        FhirContext fhirContext = FhirContext.forR4();
        IParser iParser = fhirContext.newJsonParser();

        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream("sample_diagnostic_report_with_pdf_attachment.json");
        Reader reader = new InputStreamReader(resourceAsStream);
        Bundle bundle = (Bundle) iParser.parseResource(reader);

        List<Entry> entries = new ArrayList<>();
        Entry entry = new Entry();
        entry.setContent(iParser.encodeResourceToString(bundle));
        entry.setMedia("application/fhir+json");
        entries.add(entry);
        DataNotificationRequest dataNotificationRequest =
                DataNotificationRequest.builder().transactionId("123456").entries(entries).build();
        FileUtils.serializeDataToFile(dataNotificationRequest, Paths.get("/tmp/hiu/data/Transaction123456.json")).subscribe();

    }

    private void readEncounter(Bundle bundle) {
        Bundle.BundleEntryComponent bundleEntryComponent = bundle.getEntry().get(0);
        Composition composition = (Composition) bundleEntryComponent.getResource();
        Encounter encounter = (Encounter) composition.getEncounter().getResource();
        System.out.println("Status:" + encounter.getStatus());
    }



}
