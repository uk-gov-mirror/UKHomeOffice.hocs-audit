package uk.gov.digital.ho.hocs.audit.auditdetails.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CreateAuditDto {

    @JsonProperty(value= "correlation_id", required = true)
    private String correlationID;

    @JsonProperty(value= "raising_service", required = true)
    private String raisingService;

    @JsonProperty(value= "audit_payload")
    private String auditPayload;

    @JsonProperty(value= "namespace", required = true)
    private String namespace;

    @JsonProperty(value= "type", required = true)
    private String type;

    @JsonProperty(value= "user_id", required = true)
    private String userID;

}
