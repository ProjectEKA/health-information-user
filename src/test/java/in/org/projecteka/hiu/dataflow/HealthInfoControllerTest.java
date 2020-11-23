package in.org.projecteka.hiu.dataflow;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nimbusds.jose.jwk.JWKSet;
import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.common.Authenticator;
import in.org.projecteka.hiu.consent.ConceptValidator;
import in.org.projecteka.hiu.consent.PatientConsentRepository;
import in.org.projecteka.hiu.dataflow.model.PatientDataRequestMapping;
import in.org.projecteka.hiu.dataflow.model.DataPartDetail;
import in.org.projecteka.hiu.dataflow.model.HealthInfoStatus;
import in.org.projecteka.hiu.dataflow.model.PatientHealthInfoStatus;
import in.org.projecteka.hiu.dataflow.model.DataRequestStatus;
import in.org.projecteka.hiu.dataflow.model.DataRequestStatusResponse;
import in.org.projecteka.hiu.dataflow.model.DataRequestStatusCheckRequest;
import in.org.projecteka.hiu.dataprocessor.DataAvailabilityListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static in.org.projecteka.hiu.common.Constants.API_PATH_FETCH_PATIENT_HEALTH_INFO;
import static in.org.projecteka.hiu.common.Constants.API_PATH_GET_HEALTH_INFO_STATUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static reactor.core.publisher.Mono.just;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class HealthInfoControllerTest {

    @MockBean
    @Qualifier("userAuthenticator")
    private Authenticator authenticator;

    @SuppressWarnings("unused")
    @MockBean
    @Qualifier("centralRegistryJWKSet")
    private JWKSet centralRegistryJWKSet;

    @SuppressWarnings("unused")
    @MockBean
    @Qualifier("identityServiceJWKSet")
    private JWKSet identityServiceJWKSet;

    @SuppressWarnings("unused")
    @MockBean
    private DataAvailabilityListener dataAvailabilityListener;

    @SuppressWarnings("unused")
    @MockBean
    private DestinationsConfig destinationsConfig;

    @SuppressWarnings("unused")
    @MockBean
    private DataFlowRequestListener dataFlowRequestListener;

    @SuppressWarnings("unused")
    @MockBean
    private DataFlowDeleteListener dataFlowDeleteListener;

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private DataFlowServiceProperties serviceProperties;

    @MockBean
    private PatientConsentRepository patientConsentRepository;

    @MockBean
    private DataFlowRepository dataFlowRepository;

    @MockBean
    private HealthInformationRepository healthInformationRepository;

    @SuppressWarnings("unused")
    @MockBean
    private ConceptValidator conceptValidator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        when(serviceProperties.getDefaultPageSize()).thenReturn(20);
        when(serviceProperties.getMaxPageSize()).thenReturn(20);
        when(serviceProperties.getDataFlowRequestWaitTime()).thenReturn(2);
        when(serviceProperties.getDataPartWaitTime()).thenReturn(2);
    }

    @Test
    void shouldFetchDataPartDetails() {
        ArgumentCaptor<List<String>> transactionIdsCaptor = ArgumentCaptor.forClass(List.class);

        var token = TestBuilders.string();
        var requester = "someone@ncg";
        var caller = new Caller(requester, false, null, true);
        var healthInfoRequest = TestBuilders.healthInformationRequest().limit(10).build();
        var dataRequestMappings = TestBuilders.dataRequestMappings(2).stream()
                .map(PatientDataRequestMapping.PatientDataRequestMappingBuilder::build)
                .collect(Collectors.toList());
        var consentRequestIds = dataRequestMappings.stream().map(PatientDataRequestMapping::getConsentRequestId).collect(Collectors.toList());
        var dataPartDetails = TestBuilders.dataPartDetails(2, requester, HealthInfoStatus.SUCCEEDED)
                .stream().map(DataPartDetail.DataPartDetailBuilder::build).collect(Collectors.toList());
        var transactionIds = dataPartDetails.stream().map(DataPartDetail::getTransactionId).collect(Collectors.toList());
        List<Map<String, Object>> healthInfo = List.of(Map.of(
                "data", JsonNodeFactory.instance.objectNode(),
                "status", "SUCCEEDED",
                "transaction_id", transactionIds.get(0),
                "doc_id", "1.v1",
                "doc_origin", "hip1d-11"));

        when(authenticator.verify(token)).thenReturn(just(caller));
        when(patientConsentRepository.fetchConsentRequestIds(healthInfoRequest.getRequestIds())).thenReturn(Flux.fromIterable(dataRequestMappings));
        when(dataFlowRepository.fetchDataPartDetails(consentRequestIds)).thenReturn(Flux.fromIterable(dataPartDetails));
        when(healthInformationRepository.getHealthInformation(transactionIdsCaptor.capture(), eq(healthInfoRequest.getLimit()), eq(healthInfoRequest.getOffset())))
                .thenReturn(Flux.fromIterable(healthInfo));
        when(healthInformationRepository.getTotalCountOfEntries(transactionIdsCaptor.capture())).thenReturn(Mono.just(100));

        webTestClient
                .post()
                .uri(API_PATH_FETCH_PATIENT_HEALTH_INFO)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(healthInfoRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk();

        assertEquals(Set.copyOf(transactionIds), Set.copyOf(transactionIdsCaptor.getValue()));
    }

    private void assertHealthInfoStatus(String token,
                                        DataRequestStatusCheckRequest request,
                                        DataRequestStatusResponse expectedResponse) throws JsonProcessingException {
        webTestClient
                .post()
                .uri(API_PATH_GET_HEALTH_INFO_STATUS)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json(new ObjectMapper()
                        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                        .writeValueAsString(expectedResponse));
    }

    @Test
    void shouldReturnSucceededStatusForGivenDataRequestIds() throws JsonProcessingException {
        var token = TestBuilders.string();
        var requester = "someone@ncg";
        var caller = new Caller(requester, false, null, true);
        var dataRequestIds = List.of(UUID.randomUUID().toString());
        var dataStatusCheckRequest = DataRequestStatusCheckRequest.builder().requestIds(dataRequestIds).build();
        ArgumentCaptor<Set<String>> dataRequestIdsCaptor = ArgumentCaptor.forClass(Set.class);
        var dataRequestDetail = TestBuilders.patientDataRequestDetail().dataPartStatus(HealthInfoStatus.SUCCEEDED).patientId(requester).build();

        when(authenticator.verify(token)).thenReturn(just(caller));
        when(dataFlowRepository.fetchPatientDataRequestDetails(dataRequestIdsCaptor.capture())).thenReturn(Flux.just(dataRequestDetail));

        var expectedResponse = DataRequestStatusResponse.builder().statuses(
                List.of(PatientHealthInfoStatus.builder()
                        .hipId(dataRequestDetail.getHipId())
                        .requestId(dataRequestDetail.getDataRequestId())
                        .status(DataRequestStatus.SUCCEEDED)
                        .build())).build();

        assertHealthInfoStatus(token, dataStatusCheckRequest, expectedResponse);
        assertEquals(dataRequestIdsCaptor.getValue(), Set.copyOf(dataRequestIds));
    }

    @Test
    void shouldReturnPartialStatusForGivenDataRequestIds() throws JsonProcessingException {
        var token = TestBuilders.string();
        var requester = "someone@ncg";
        var caller = new Caller(requester, false, null, true);
        var dataRequestIds = List.of(UUID.randomUUID().toString());
        var dataStatusCheckRequest = DataRequestStatusCheckRequest.builder().requestIds(dataRequestIds).build();
        ArgumentCaptor<Set<String>> dataRequestIdsCaptor = ArgumentCaptor.forClass(Set.class);
        var dataRequestDetail = TestBuilders.patientDataRequestDetail().dataPartStatus(HealthInfoStatus.PARTIAL).patientId(requester).build();

        when(authenticator.verify(token)).thenReturn(just(caller));
        when(dataFlowRepository.fetchPatientDataRequestDetails(dataRequestIdsCaptor.capture())).thenReturn(Flux.just(dataRequestDetail));

        var expectedResponse = DataRequestStatusResponse.builder().statuses(
                List.of(PatientHealthInfoStatus.builder()
                        .hipId(dataRequestDetail.getHipId())
                        .requestId(dataRequestDetail.getDataRequestId())
                        .status(DataRequestStatus.PARTIAL)
                        .build())).build();

        assertHealthInfoStatus(token, dataStatusCheckRequest, expectedResponse);
        assertEquals(dataRequestIdsCaptor.getValue(), Set.copyOf(dataRequestIds));
    }

    @Test
    void shouldReturnProcessingStatusWhenDataPartIsBeingProcessed() throws JsonProcessingException {
        var token = TestBuilders.string();
        var requester = "someone@ncg";
        var caller = new Caller(requester, false, null, true);
        var dataRequestIds = List.of(UUID.randomUUID().toString());
        var dataStatusCheckRequest = DataRequestStatusCheckRequest.builder().requestIds(dataRequestIds).build();
        ArgumentCaptor<Set<String>> dataRequestIdsCaptor = ArgumentCaptor.forClass(Set.class);
        var dataRequestDetail = TestBuilders.patientDataRequestDetail().dataPartStatus(HealthInfoStatus.PROCESSING).patientId(requester).build();

        when(authenticator.verify(token)).thenReturn(just(caller));
        when(dataFlowRepository.fetchPatientDataRequestDetails(dataRequestIdsCaptor.capture())).thenReturn(Flux.just(dataRequestDetail));

        var expectedResponse = DataRequestStatusResponse.builder().statuses(
                List.of(PatientHealthInfoStatus.builder()
                        .hipId(dataRequestDetail.getHipId())
                        .requestId(dataRequestDetail.getDataRequestId())
                        .status(DataRequestStatus.PROCESSING)
                        .build())).build();

        assertHealthInfoStatus(token, dataStatusCheckRequest, expectedResponse);
        assertEquals(dataRequestIdsCaptor.getValue(), Set.copyOf(dataRequestIds));
    }

    @Test
    void shouldReturnErroredStatusWhenDataPartHasAllEntriesErrored() throws JsonProcessingException {
        var token = TestBuilders.string();
        var requester = "someone@ncg";
        var caller = new Caller(requester, false, null, true);
        var dataRequestIds = List.of(UUID.randomUUID().toString());
        var dataStatusCheckRequest = DataRequestStatusCheckRequest.builder().requestIds(dataRequestIds).build();
        ArgumentCaptor<Set<String>> dataRequestIdsCaptor = ArgumentCaptor.forClass(Set.class);
        var dataRequestDetail = TestBuilders.patientDataRequestDetail().dataPartStatus(HealthInfoStatus.ERRORED).patientId(requester).build();

        when(authenticator.verify(token)).thenReturn(just(caller));
        when(dataFlowRepository.fetchPatientDataRequestDetails(dataRequestIdsCaptor.capture())).thenReturn(Flux.just(dataRequestDetail));

        var expectedResponse = DataRequestStatusResponse.builder().statuses(
                List.of(PatientHealthInfoStatus.builder()
                        .hipId(dataRequestDetail.getHipId())
                        .requestId(dataRequestDetail.getDataRequestId())
                        .status(DataRequestStatus.ERRORED)
                        .build())).build();

        assertHealthInfoStatus(token, dataStatusCheckRequest, expectedResponse);
        assertEquals(dataRequestIdsCaptor.getValue(), Set.copyOf(dataRequestIds));
    }

    @Test
    void shouldReturnProcessingStatusWhenConsentRequestIsNotCreatedAndWaitingTimeIsNotPassed() throws JsonProcessingException {
        var token = TestBuilders.string();
        var requester = "someone@ncg";
        var caller = new Caller(requester, false, null, true);
        var dataRequestIds = List.of(UUID.randomUUID().toString());
        var dataStatusCheckRequest = DataRequestStatusCheckRequest.builder().requestIds(dataRequestIds).build();
        ArgumentCaptor<Set<String>> dataRequestIdsCaptor = ArgumentCaptor.forClass(Set.class);
        var dataRequestDetail = TestBuilders.patientDataRequestDetail()
                .consentRequestId(null)
                .patientDataRequestedAt(LocalDateTime.now(ZoneOffset.UTC))
                .patientId(requester)
                .build();

        when(authenticator.verify(token)).thenReturn(just(caller));
        when(dataFlowRepository.fetchPatientDataRequestDetails(dataRequestIdsCaptor.capture())).thenReturn(Flux.just(dataRequestDetail));

        var expectedResponse = DataRequestStatusResponse.builder().statuses(
                List.of(PatientHealthInfoStatus.builder()
                        .hipId(dataRequestDetail.getHipId())
                        .requestId(dataRequestDetail.getDataRequestId())
                        .status(DataRequestStatus.PROCESSING)
                        .build())).build();

        assertHealthInfoStatus(token, dataStatusCheckRequest, expectedResponse);
        assertEquals(dataRequestIdsCaptor.getValue(), Set.copyOf(dataRequestIds));
    }

    @Test
    void shouldReturnProcessingStatusWhenConsentArtefactIsNotReachedAndWaitingTimeIsNotPassed() throws JsonProcessingException {
        var token = TestBuilders.string();
        var requester = "someone@ncg";
        var caller = new Caller(requester, false, null, true);
        var dataRequestIds = List.of(UUID.randomUUID().toString());
        var dataStatusCheckRequest = DataRequestStatusCheckRequest.builder().requestIds(dataRequestIds).build();
        ArgumentCaptor<Set<String>> dataRequestIdsCaptor = ArgumentCaptor.forClass(Set.class);
        var dataRequestDetail = TestBuilders.patientDataRequestDetail()
                .consentArtefactId(null)
                .patientId(requester)
                .patientDataRequestedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .build();

        when(authenticator.verify(token)).thenReturn(just(caller));
        when(dataFlowRepository.fetchPatientDataRequestDetails(dataRequestIdsCaptor.capture())).thenReturn(Flux.just(dataRequestDetail));

        var expectedResponse = DataRequestStatusResponse.builder().statuses(
                List.of(PatientHealthInfoStatus.builder()
                        .hipId(dataRequestDetail.getHipId())
                        .requestId(dataRequestDetail.getDataRequestId())
                        .status(DataRequestStatus.PROCESSING)
                        .build())).build();

        assertHealthInfoStatus(token, dataStatusCheckRequest, expectedResponse);
        assertEquals(dataRequestIdsCaptor.getValue(), Set.copyOf(dataRequestIds));
    }

    @Test
    void shouldReturnProcessingStatusWhenDataFlowIsNotReachedAndWaitingTimeIsNotPassed() throws JsonProcessingException {
        var token = TestBuilders.string();
        var requester = "someone@ncg";
        var caller = new Caller(requester, false, null, true);
        var dataRequestIds = List.of(UUID.randomUUID().toString());
        var dataStatusCheckRequest = DataRequestStatusCheckRequest.builder().requestIds(dataRequestIds).build();
        ArgumentCaptor<Set<String>> dataRequestIdsCaptor = ArgumentCaptor.forClass(Set.class);
        var dataRequestDetail = TestBuilders.patientDataRequestDetail()
                .dataPartStatus(null)
                .dataFlowRequestedAt(LocalDateTime.now(ZoneOffset.UTC))
                .patientId(requester)
                .build();

        when(authenticator.verify(token)).thenReturn(just(caller));
        when(dataFlowRepository.fetchPatientDataRequestDetails(dataRequestIdsCaptor.capture())).thenReturn(Flux.just(dataRequestDetail));

        var expectedResponse = DataRequestStatusResponse.builder().statuses(
                List.of(PatientHealthInfoStatus.builder()
                        .hipId(dataRequestDetail.getHipId())
                        .requestId(dataRequestDetail.getDataRequestId())
                        .status(DataRequestStatus.PROCESSING)
                        .build())).build();

        assertHealthInfoStatus(token, dataStatusCheckRequest, expectedResponse);
        assertEquals(dataRequestIdsCaptor.getValue(), Set.copyOf(dataRequestIds));
    }

    @Test
    void shouldReturnErroredStatusWhenDataFlowIsNotReachedWithinWatingTimeLimit() throws JsonProcessingException {
        var token = TestBuilders.string();
        var requester = "someone@ncg";
        var caller = new Caller(requester, false, null, true);
        var dataRequestIds = List.of(UUID.randomUUID().toString());
        var dataStatusCheckRequest = DataRequestStatusCheckRequest.builder().requestIds(dataRequestIds).build();
        ArgumentCaptor<Set<String>> dataRequestIdsCaptor = ArgumentCaptor.forClass(Set.class);
        var dataRequestDetail = TestBuilders.patientDataRequestDetail()
                .dataPartStatus(null)
                .patientId(requester)
                .dataFlowRequestedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5))
                .build();

        when(authenticator.verify(token)).thenReturn(just(caller));
        when(dataFlowRepository.fetchPatientDataRequestDetails(dataRequestIdsCaptor.capture())).thenReturn(Flux.just(dataRequestDetail));

        var expectedResponse = DataRequestStatusResponse.builder().statuses(
                List.of(PatientHealthInfoStatus.builder()
                        .hipId(dataRequestDetail.getHipId())
                        .requestId(dataRequestDetail.getDataRequestId())
                        .status(DataRequestStatus.ERRORED)
                        .build())).build();

        assertHealthInfoStatus(token, dataStatusCheckRequest, expectedResponse);
        assertEquals(dataRequestIdsCaptor.getValue(), Set.copyOf(dataRequestIds));
    }

    @Test
    void shouldReturnErroredStatusWhenConsentArtefactNotReachedWithinWatingTimeLimit() throws JsonProcessingException {
        var token = TestBuilders.string();
        var requester = "someone@ncg";
        var caller = new Caller(requester, false, null, true);
        var dataRequestIds = List.of(UUID.randomUUID().toString());
        var dataStatusCheckRequest = DataRequestStatusCheckRequest.builder().requestIds(dataRequestIds).build();
        ArgumentCaptor<Set<String>> dataRequestIdsCaptor = ArgumentCaptor.forClass(Set.class);
        var dataRequestDetail = TestBuilders.patientDataRequestDetail()
                .consentArtefactId(null)
                .patientDataRequestedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5))
                .patientId(requester)
                .build();

        when(authenticator.verify(token)).thenReturn(just(caller));
        when(dataFlowRepository.fetchPatientDataRequestDetails(dataRequestIdsCaptor.capture())).thenReturn(Flux.just(dataRequestDetail));

        var expectedResponse = DataRequestStatusResponse.builder().statuses(
                List.of(PatientHealthInfoStatus.builder()
                        .hipId(dataRequestDetail.getHipId())
                        .requestId(dataRequestDetail.getDataRequestId())
                        .status(DataRequestStatus.ERRORED)
                        .build())).build();

        assertHealthInfoStatus(token, dataStatusCheckRequest, expectedResponse);
        assertEquals(dataRequestIdsCaptor.getValue(), Set.copyOf(dataRequestIds));
    }

    @Test
    void shouldReturnErroredStatusWhenConsentRequestIsNotCreatedWithinWatingTimeLimit() throws JsonProcessingException {
        var token = TestBuilders.string();
        var requester = "someone@ncg";
        var caller = new Caller(requester, false, null, true);
        var dataRequestIds = List.of(UUID.randomUUID().toString());
        var dataStatusCheckRequest = DataRequestStatusCheckRequest.builder().requestIds(dataRequestIds).build();
        ArgumentCaptor<Set<String>> dataRequestIdsCaptor = ArgumentCaptor.forClass(Set.class);
        var dataRequestDetail = TestBuilders.patientDataRequestDetail()
                .consentRequestId(null)
                .patientDataRequestedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5))
                .patientId(requester)
                .build();

        when(authenticator.verify(token)).thenReturn(just(caller));
        when(dataFlowRepository.fetchPatientDataRequestDetails(dataRequestIdsCaptor.capture())).thenReturn(Flux.just(dataRequestDetail));

        var expectedResponse = DataRequestStatusResponse.builder().statuses(
                List.of(PatientHealthInfoStatus.builder()
                        .hipId(dataRequestDetail.getHipId())
                        .requestId(dataRequestDetail.getDataRequestId())
                        .status(DataRequestStatus.ERRORED)
                        .build())).build();

        assertHealthInfoStatus(token, dataStatusCheckRequest, expectedResponse);
        assertEquals(dataRequestIdsCaptor.getValue(), Set.copyOf(dataRequestIds));
    }

    @Test
    void shouldReturnEmptyStatusesDataRequestIdDoesntBelongToRequester() throws JsonProcessingException {
        var token = TestBuilders.string();
        var requester = "someone@ncg";
        var caller = new Caller(requester, false, null, true);
        var dataRequestIds = List.of(UUID.randomUUID().toString());
        var dataStatusCheckRequest = DataRequestStatusCheckRequest.builder().requestIds(dataRequestIds).build();
        ArgumentCaptor<Set<String>> dataRequestIdsCaptor = ArgumentCaptor.forClass(Set.class);
        var dataRequestDetail = TestBuilders.patientDataRequestDetail()
                .consentRequestId(null)
                .patientDataRequestedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5))
                .patientId("some-other-request@ncg")
                .build();

        when(authenticator.verify(token)).thenReturn(just(caller));
        when(dataFlowRepository.fetchPatientDataRequestDetails(dataRequestIdsCaptor.capture())).thenReturn(Flux.just(dataRequestDetail));


        assertHealthInfoStatus(token, dataStatusCheckRequest, new DataRequestStatusResponse(List.of()));
        assertEquals(dataRequestIdsCaptor.getValue(), Set.copyOf(dataRequestIds));
    }
}
