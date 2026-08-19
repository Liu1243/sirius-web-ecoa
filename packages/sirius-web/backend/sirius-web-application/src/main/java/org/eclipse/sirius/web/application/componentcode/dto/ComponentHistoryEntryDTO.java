package org.eclipse.sirius.web.application.componentcode.dto;

import java.util.List;

public record ComponentHistoryEntryDTO(
    String componentId,
    String componentName,
    List<ComponentCodeVersionDTO> versions
) {
}
