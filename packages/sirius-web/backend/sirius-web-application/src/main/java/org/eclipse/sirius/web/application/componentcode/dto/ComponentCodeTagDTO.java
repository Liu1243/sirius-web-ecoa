package org.eclipse.sirius.web.application.componentcode.dto;

import java.util.UUID;

public record ComponentCodeTagDTO(
    UUID id,
    String name,
    String color
) {
}
