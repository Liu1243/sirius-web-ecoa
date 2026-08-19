package org.eclipse.sirius.web.edt.componentcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.sirius.web.application.capability.services.api.ICapabilityEvaluator;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeVersionService;
import org.eclipse.sirius.web.application.project.services.api.IProjectEditingContextService;
import org.eclipse.sirius.web.domain.boundedcontexts.project.services.api.IProjectSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

public class ComponentCodeImportExportControllerTest {

    private final IComponentCodeVersionService versionService = mock(IComponentCodeVersionService.class);
    private final IProjectSearchService projectSearchService = mock(IProjectSearchService.class);
    private final IProjectEditingContextService projectEditingContextService = mock(IProjectEditingContextService.class);
    private final ICapabilityEvaluator capabilityEvaluator = mock(ICapabilityEvaluator.class);
    private final ComponentCodeImportExportController controller =
        new ComponentCodeImportExportController(versionService, projectSearchService, projectEditingContextService, capabilityEvaluator);

    @Test
    public void getPendingVersions_returnsEmptyListWhenNoPending() {
        String projectId = UUID.randomUUID().toString();
        when(projectSearchService.findById(projectId)).thenReturn(Optional.of(mock(org.eclipse.sirius.web.domain.boundedcontexts.project.Project.class)));
        when(versionService.getPendingVersions(UUID.fromString(projectId))).thenReturn(List.of());

        var response = controller.getPendingVersions(projectId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("versions")).isEmpty();
    }

    @Test
    public void getPendingVersions_returns404ForUnknownProject() {
        String projectId = UUID.randomUUID().toString();
        when(projectSearchService.findById(projectId)).thenReturn(Optional.empty());

        var response = controller.getPendingVersions(projectId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void confirmPendingVersions_callsServiceWithCorrectIds() {
        String projectId = UUID.randomUUID().toString();
        UUID acceptedId = UUID.randomUUID();
        UUID rejectedId = UUID.randomUUID();
        when(projectSearchService.findById(projectId)).thenReturn(Optional.of(mock(org.eclipse.sirius.web.domain.boundedcontexts.project.Project.class)));
        when(capabilityEvaluator.hasCapability(any(), any(), any())).thenReturn(true);

        var body = new ComponentCodeImportExportController.ConfirmRequest(
            List.of(acceptedId), List.of(rejectedId));
        var response = controller.confirmPendingVersions(projectId, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(versionService).confirmPendingVersions(UUID.fromString(projectId),
            List.of(acceptedId), List.of(rejectedId));
    }
}
