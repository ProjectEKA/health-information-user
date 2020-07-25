package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestResult;
import in.org.projecteka.hiu.dataflow.model.DataPartDetail;
import in.org.projecteka.hiu.dataflow.model.Entry;
import in.org.projecteka.hiu.dataflow.model.HealthInfoStatus;
import in.org.projecteka.hiu.dataflow.model.HealthInformationFetchRequest;
import in.org.projecteka.hiu.dataflow.model.KeyMaterial;
import in.org.projecteka.hiu.dataflow.model.PatientDataRequestMapping;
import in.org.projecteka.hiu.dataflow.model.PatientDataRequestDetail;
import org.jeasy.random.EasyRandom;

import java.util.List;
import java.util.stream.Collectors;

public class TestBuilders {
    private static final EasyRandom easyRandom = new EasyRandom();

    public static DataFlowRequest.DataFlowRequestBuilder dataFlowRequest() {
        return easyRandom.nextObject(DataFlowRequest.DataFlowRequestBuilder.class);
    }

    public static Entry.EntryBuilder entry() {
        return easyRandom.nextObject(Entry.EntryBuilder.class);
    }

    public static KeyMaterial.KeyMaterialBuilder keyMaterial() {
        return easyRandom.nextObject(KeyMaterial.KeyMaterialBuilder.class);
    }

    public static DataFlowRequestKeyMaterial.DataFlowRequestKeyMaterialBuilder dataFlowRequestKeyMaterial() {
        return easyRandom.nextObject(DataFlowRequestKeyMaterial.DataFlowRequestKeyMaterialBuilder.class);
    }

    public static DataFlowRequestResult.DataFlowRequestResultBuilder dataFlowRequestResult() {
        return easyRandom.nextObject(DataFlowRequestResult.DataFlowRequestResultBuilder.class);
    }

    public static HealthInformationFetchRequest.HealthInformationFetchRequestBuilder healthInformationRequest() {
        return easyRandom.nextObject(HealthInformationFetchRequest.HealthInformationFetchRequestBuilder.class);
    }

    public static List<DataPartDetail.DataPartDetailBuilder> dataPartDetails(int size, String requester, HealthInfoStatus status) {
        var builders = easyRandom.objects(DataPartDetail.DataPartDetailBuilder.class, size);
        return builders.map(builder -> builder.requester(requester).status(status)).collect(Collectors.toList());
    }


    public static DataPartDetail.DataPartDetailBuilder dataPartDetail() {
        return easyRandom.nextObject(DataPartDetail.DataPartDetailBuilder.class);
    }


    public static List<PatientDataRequestMapping.PatientDataRequestMappingBuilder> dataRequestMappings(int size) {
        return easyRandom.objects(PatientDataRequestMapping.PatientDataRequestMappingBuilder.class, size)
                .collect(Collectors.toList());
    }

    public static PatientDataRequestMapping.PatientDataRequestMappingBuilder dataRequestMapping() {
        return easyRandom.nextObject(PatientDataRequestMapping.PatientDataRequestMappingBuilder.class);
    }

    public static PatientDataRequestDetail.PatientDataRequestDetailBuilder patientDataRequestDetail() {
        return easyRandom.nextObject(PatientDataRequestDetail.PatientDataRequestDetailBuilder.class);
    }

    public static String string() {
        return easyRandom.nextObject(String.class);
    }
}
