package uk.gov.digital.ho.hocs.audit.export;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.digital.ho.hocs.audit.application.SpringConfiguration;
import uk.gov.digital.ho.hocs.audit.auditdetails.model.AuditData;
import uk.gov.digital.ho.hocs.audit.auditdetails.repository.AuditRepository;
import uk.gov.digital.ho.hocs.audit.export.infoclient.InfoClient;
import uk.gov.digital.ho.hocs.audit.export.infoclient.dto.CaseTypeDto;
import java.io.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AuditExportServiceTest {

    @Mock
    private AuditRepository auditRepository;

    @Mock
    private InfoClient infoClient;

    private ExportService exportService;
    private SpringConfiguration configuration = new SpringConfiguration();
    private ObjectMapper mapper;
    private Set<CaseTypeDto> caseTypes = new HashSet<CaseTypeDto>() {{
        add(new CaseTypeDto("DCU Ministerial", "a1", "MIN"));
    }};
    private LocalDateTime from = LocalDateTime.of(2019, 1, 1, 0, 0);
    private LocalDateTime to = LocalDateTime.of(LocalDate.of(2019, 6, 1), LocalTime.MAX);
    private String caseType = "MIN";
    private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LinkedHashSet<String> fields = Stream.of(
            "CopyNumberTen",
            "DateOfCorrespondence",
            "OGDDept",
            "InitialDraftDecision",
            "PrivateOfficeDecision",
            "Topics",
            "MarkupDecision",
            "NoReplyNeededConfirmation",
            "OverridePOTeamUUID",
            "QAResponseDecision",
            "DateReceived",
            "OfflineQA",
            "OverrideDraftingTeamUUID",
            "OriginalChannel",
            "ResponseChannel",
            "OfflineQaUser",
            "DraftingTeamName",
            "POTeamName",
            "Correspondents",
            "TransferConfirmation",
            "MinisterSignOffDecision",
            "PrivateOfficeOverridePOTeamUUID",
            "DispatchDecision").collect(Collectors.toCollection(LinkedHashSet::new));

    @Before
    public void setup() {
        mapper = configuration.initialiseObjectMapper();
        when(infoClient.getCaseTypes()).thenReturn(caseTypes);
        exportService = new ExportService(auditRepository, mapper, infoClient);
    }

    @Test
    public void caseDataExportShouldReturnRowHeaders() throws IOException {
        Set<String> expectedHeaders = Stream.of("timestamp", "event", "userId", "caseUuid", "reference", "caseType", "deadline", "primaryCorrespondent", "primaryTopic").collect(Collectors.toSet());
        expectedHeaders.addAll(fields);

        when(infoClient.getCaseExportFields("MIN")).thenReturn(fields);
        when(auditRepository.findAuditDataByDateRangeAndEvents(any(), any(), any(), any())).thenReturn(getCaseDataAuditData().stream());

        OutputStream outputStream = new ByteArrayOutputStream();
        exportService.auditExport(from.toLocalDate(), to.toLocalDate(), outputStream, caseType, ExportType.CASE_DATA);

        String csvBody = outputStream.toString();
        Set<String> headers = getCSVHeaders(csvBody).keySet();
        assertThat(headers).containsExactlyInAnyOrder(expectedHeaders.toArray(new String[expectedHeaders.size()]));
    }

    @Test
    public void caseDataExportShouldOnlyRequestCreateUpdateEventsAndCaseType() throws IOException {

        when(infoClient.getCaseExportFields("MIN")).thenReturn(fields);

        when(auditRepository.findAuditDataByDateRangeAndEvents(any(), any(), eq(ExportService.CASE_DATA_EVENTS), any())).thenReturn(getCaseDataAuditData().stream());

        OutputStream outputStream = new ByteArrayOutputStream();
        exportService.auditExport(from.toLocalDate(), to.toLocalDate(), outputStream, caseType, ExportType.CASE_DATA);

        verify(auditRepository, times(1)).findAuditDataByDateRangeAndEvents(from, to, ExportService.CASE_DATA_EVENTS, "a1");
    }

    @Test
    public void caseDataExportShouldReturnCSVData() throws IOException {

        when(infoClient.getCaseExportFields("MIN")).thenReturn(fields);

        when(auditRepository.findAuditDataByDateRangeAndEvents(any(), any(), eq(ExportService.CASE_DATA_EVENTS), any())).thenReturn(getCaseDataAuditData().stream());

        OutputStream outputStream = new ByteArrayOutputStream();
        exportService.auditExport(from.toLocalDate(), to.toLocalDate(), outputStream, caseType, ExportType.CASE_DATA);

        verify(auditRepository, times(1)).findAuditDataByDateRangeAndEvents(from, to, ExportService.CASE_DATA_EVENTS, "a1");
        List<CSVRecord> rows = getCSVRows(outputStream.toString());
        assertThat(rows.size()).isEqualTo(3);

        CSVRecord row = rows.get(0);
        assertThat(row.get("DateReceived")).isEqualTo("2019-04-23");
        assertThat(row.get("CopyNumberTen")).isEqualTo("FALSE");
        assertThat(row.get("Correspondents")).isEqualTo("09a89901-d2f1-4778-befe-ebab57659b90");
        assertThat(row.get("OriginalChannel")).isEqualTo("EMAIL");
        assertThat(row.get("DateOfCorrespondence")).isEqualTo("2019-04-23");
        assertThat(row.get("caseType")).isEqualTo("MIN");
        assertThat(row.get("caseUuid")).isEqualTo("3e5cf44f-e86a-4b21-891a-018e2343cda1");
        assertThat(row.get("reference")).isEqualTo("MIN/0120101/19");
        assertThat(row.get("deadline")).isEqualTo("2019-05-22");
        assertThat(row.get("DateReceived")).isEqualTo("2019-04-23");
        assertThat(row.get("primaryTopic")).isEmpty();
        assertThat(row.get("primaryCorrespondent")).isEqualTo("09a89901-d2f1-4778-befe-ebab57659b90");
    }

    @Test
    public void caseTopicExportShouldReturnRowHeaders() throws IOException {
        String[] expectedHeaders = new String[]{"timestamp", "event" ,"userId", "caseUuid", "topicUuid", "topic"};

        when(auditRepository.findAuditDataByDateRangeAndEvents(any(), any(), any(), any())).thenReturn(getTopicDataAuditData().stream());

        OutputStream outputStream = new ByteArrayOutputStream();
        exportService.auditExport(from.toLocalDate(), to.toLocalDate(), outputStream, caseType, ExportType.TOPICS);

        String csvBody = outputStream.toString();
        Set<String> headers = getCSVHeaders(csvBody).keySet();
        assertThat(headers).containsExactlyInAnyOrder(expectedHeaders);
    }

    @Test
    public void caseTopicExportShouldOnlyRequestCreateUpdateEventsAndCaseType() throws IOException {
        OutputStream outputStream = new ByteArrayOutputStream();
        when(auditRepository.findAuditDataByDateRangeAndEvents(any(), any(), any(), any())).thenReturn(getTopicDataAuditData().stream());
        exportService.auditExport(from.toLocalDate(), to.toLocalDate(), outputStream, caseType, ExportType.TOPICS);
        verify(auditRepository, times(1)).findAuditDataByDateRangeAndEvents(from, to, ExportService.TOPIC_EVENTS, "a1");
    }

    @Test
    public void caseCorrespondentsExportShouldReturnRowHeaders() throws IOException {
        String[] expectedHeaders = new String[]{"timestamp", "event" ,"userId","caseUuid",
                "correspondentUuid", "fullname", "address1", "address2",
                "address3", "country", "postcode", "telephone", "email",
                "reference"};

        when(auditRepository.findAuditDataByDateRangeAndEvents(any(), any(), any(), any())).thenReturn(getCorrespondentDataAuditData().stream());

        OutputStream outputStream = new ByteArrayOutputStream();
        exportService.auditExport(from.toLocalDate(), to.toLocalDate(), outputStream, caseType, ExportType.CORRESPONDENTS);

        String csvBody = outputStream.toString();
        Set<String> headers = getCSVHeaders(csvBody).keySet();
        assertThat(headers).containsExactlyInAnyOrder(expectedHeaders);
    }

    @Test
    public void caseCorrespondentExportShouldOnlyRequestCreateUpdateEventsAndCaseType() throws IOException {
        when(auditRepository.findAuditDataByDateRangeAndEvents(any(), any(), any(), any())).thenReturn(getCorrespondentDataAuditData().stream());
        OutputStream outputStream = new ByteArrayOutputStream();
        exportService.auditExport(from.toLocalDate(), to.toLocalDate(), outputStream, caseType, ExportType.CORRESPONDENTS);
        verify(auditRepository, times(1)).findAuditDataByDateRangeAndEvents(from, to, ExportService.CORRESPONDENT_EVENTS, "a1");
    }

    @Test
    public void caseAllocationsExportShouldReturnRowHeaders() throws IOException {
        String[] expectedHeaders = new String[]{"timestamp", "event" ,"userId","caseUuid","stage", "teamUuid"};
        when(auditRepository.findAuditDataByDateRangeAndEvents(any(), any(), any(), any())).thenReturn(getAllocationDataAuditData().stream());
        OutputStream outputStream = new ByteArrayOutputStream();
        exportService.auditExport(from.toLocalDate(), to.toLocalDate(), outputStream, caseType, ExportType.ALLOCATIONS);

        String csvBody = outputStream.toString();
        Set<String> headers = getCSVHeaders(csvBody).keySet();
        assertThat(headers).containsExactlyInAnyOrder(expectedHeaders);
    }

    @Test
    public void caseAllocationsExportShouldOnlyRequestCreateUpdateEventsAndCaseType() throws IOException {
        when(auditRepository.findAuditDataByDateRangeAndEvents(any(), any(), any(), any())).thenReturn(getAllocationDataAuditData().stream());
        OutputStream outputStream = new ByteArrayOutputStream();
        exportService.auditExport(from.toLocalDate(), to.toLocalDate(), outputStream, caseType, ExportType.ALLOCATIONS);
        verify(auditRepository, times(1)).findAuditDataByDateRangeAndEvents(from, to, ExportService.ALLOCATION_EVENTS, "a1");
    }

    private List<CSVRecord> getCSVRows(String csvBody) throws IOException {
        StringReader reader = new StringReader(csvBody);
        CSVParser csvParser = new CSVParser(reader, CSVFormat.EXCEL.withFirstRecordAsHeader().withTrim());
        return csvParser.getRecords();
    }

    private Map<String, Integer> getCSVHeaders(String csvBody) throws IOException {
        StringReader reader = new StringReader(csvBody);
        CSVParser csvParser = new CSVParser(reader, CSVFormat.EXCEL.withFirstRecordAsHeader().withTrim());
        return csvParser.getHeaderMap();
    }

    private Set<AuditData> getCaseDataAuditData() {
         return new HashSet<AuditData>(){{
            add(new AuditData(UUID.fromString("3e5cf44f-e86a-4b21-891a-018e2343cda1"),UUID.randomUUID(),UUID.randomUUID().toString(),"a-service", "{\"data\": {\"valid\": \"true\", \"DateReceived\": \"2019-04-23\", \"CopyNumberTen\": \"FALSE\", \"Correspondents\": \"09a89901-d2f1-4778-befe-ebab57659b90\", \"OriginalChannel\": \"EMAIL\", \"DateOfCorrespondence\": \"2019-04-23\"}, \"type\": \"MIN\", \"uuid\": \"3e5cf44f-e86a-4b21-891a-018e2343cda1\", \"created\": \"2019-04-23T12:57:19.738532\", \"reference\": \"MIN/0120101/19\", \"caseDeadline\": \"2019-05-22\", \"dateReceived\": \"2019-04-23\", \"primaryTopic\": null, \"primaryCorrespondent\": \"09a89901-d2f1-4778-befe-ebab57659b90\"}", "an-env", LocalDateTime.parse("2019-04-23 12:58:04",dateFormatter), "CASE_UPDATED", UUID.randomUUID().toString()));
             add(new AuditData(UUID.fromString("3e5cf44f-e86a-4b21-891a-018e2343cda1"),UUID.randomUUID(),UUID.randomUUID().toString(),"a-service", "{\"type\": \"MIN\", \"uuid\": \"3e5cf44f-e86a-4b21-891a-018e2343cda1\", \"created\": \"2019-04-23T09:18:26.446343\", \"reference\": \"MIN/0120091/19\", \"caseDeadline\": \"2019-05-22\", \"dateReceived\": \"2019-04-23\"}", "an-env", LocalDateTime.parse("2019-04-23 09:18:26", dateFormatter), "CASE_CREATED", UUID.randomUUID().toString()));
            add(new AuditData(UUID.fromString("a7590ff3-4377-4ee8-a165-0c6426c744a1"),UUID.randomUUID(),UUID.randomUUID().toString(),"a-service", "{\"type\": \"MIN\", \"uuid\": \"a7590ff3-4377-4ee8-a165-0c6426c744a1\", \"created\": \"2019-04-23T11:17:53.155776\", \"reference\": \"MIN/0120092/19\", \"caseDeadline\": \"2019-05-22\", \"dateReceived\": \"2019-04-23\"}", "an-env", LocalDateTime.parse("2019-04-23 11:17:53", dateFormatter), "CASE_CREATED", UUID.randomUUID().toString()));

        }};
    }

    private Set<AuditData> getTopicDataAuditData() {
        return new HashSet<AuditData>(){{
            add(new AuditData(UUID.fromString("3e5cf44f-e86a-4b21-891a-018e2343cda1"),UUID.randomUUID(),UUID.randomUUID().toString(),"a-service", "{\"topicName\": \"Cardiff University Kittens\", \"topicUuid\": \"56926a98-de02-49c6-8457-4e6782ac7d6e\"}", "an-env", LocalDateTime.parse("2019-04-23 12:48:33",dateFormatter), "CASE_TOPIC_CREATED", UUID.randomUUID().toString()));
        }};
    }

    private Set<AuditData> getCorrespondentDataAuditData() {
        return new HashSet<AuditData>(){{
            add(new AuditData(UUID.fromString("3e5cf44f-e86a-4b21-891a-018e2343cda1"),UUID.randomUUID(),UUID.randomUUID().toString(),"a-service", "{\"type\": \"MEMBER\", \"uuid\": \"09a89901-d2f1-4778-befe-ebab57659b90\", \"email\": null, \"address\": {\"country\": \"United Kingdom\", \"address1\": \"House of Commons\", \"address2\": \"London\", \"address3\": null, \"postcode\": \"SW1A 0AA\"}, \"created\": \"2019-04-23T12:57:58.823287\", \"caseUUID\": \"3e5cf44f-e86a-4b21-891a-018e2343cda1\", \"fullname\": \"Christina Rees MP\", \"reference\": null, \"telephone\": null}", "an-env", LocalDateTime.parse("2019-04-23 12:57:58",dateFormatter), "CORRESPONDENT_CREATED", UUID.randomUUID().toString()));
        }};
    }

    private Set<AuditData> getAllocationDataAuditData() {
        return new HashSet<AuditData>(){{
            add(new AuditData(UUID.fromString("3e5cf44f-e86a-4b21-891a-018e2343cda1"),UUID.randomUUID(),UUID.randomUUID().toString(),"a-service", "{\"stage\": \"DCU_MIN_DATA_INPUT\", \"teamUUID\": \"1102b26b-06ed-4247-a1b3-699167f2dbcd\", \"stageUUID\": \"808be858-1a4d-4117-99c8-59cf6f90edb3\"}", "an-env", LocalDateTime.parse("2019-04-23 12:58:04",dateFormatter), "STAGE_ALLOCATED_TO_TEAM", UUID.randomUUID().toString()));
            add(new AuditData(UUID.fromString("3e5cf44f-e86a-4b21-891a-018e2343cda1"),UUID.randomUUID(),UUID.randomUUID().toString(),"a-service", "{\"stage\": \"DCU_MIN_DATA_INPUT\", \"teamUUID\": \"1102b26b-06ed-4247-a1b3-699167f2dbcd\", \"stageUUID\": \"808be858-1a4d-4117-99c8-59cf6f90edb3\"}", "an-env", LocalDateTime.parse("2019-04-23 09:18:29",dateFormatter), "STAGE_ALLOCATED_TO_TEAM", UUID.randomUUID().toString()));
            add(new AuditData(UUID.fromString("a7590ff3-4377-4ee8-a165-0c6426c744a1"),UUID.randomUUID(),UUID.randomUUID().toString(),"a-service", "{\"stage\": \"DCU_MIN_DATA_INPUT\", \"teamUUID\": \"1102b26b-06ed-4247-a1b3-699167f2dbcd\", \"stageUUID\": \"64b4c266-7671-4049-882e-82b1269570c2\"}", "an-env", LocalDateTime.parse("2019-04-23 11:17:53", dateFormatter), "STAGE_ALLOCATED_TO_TEAM", UUID.randomUUID().toString()));
        }};
    }

}
