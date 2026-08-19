package org.eclipse.sirius.web.application.componentcode.dto;

import java.util.List;

public record ComponentCodeHistoryDTO(
    List<ComponentHistoryEntryDTO> components
) {
}
