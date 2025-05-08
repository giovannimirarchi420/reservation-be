package it.polito.cloudresources.be.dto.logs;

import it.polito.cloudresources.be.dto.AuditLogDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponseDTO {

    private List<AuditLogDTO> content;
    private int totalElements;

}
