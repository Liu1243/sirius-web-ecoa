package org.eclipse.sirius.web.application.project.listeners;

import java.util.Objects;
import java.util.UUID;

import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.eclipse.sirius.components.emf.ResourceMetadataAdapter;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextPersistenceService;
import org.eclipse.sirius.web.domain.boundedcontexts.project.events.ProjectNameUpdatedEvent;
import org.eclipse.sirius.web.domain.boundedcontexts.projectsemanticdata.ProjectSemanticData;
import org.eclipse.sirius.web.domain.boundedcontexts.projectsemanticdata.services.api.IProjectSemanticDataSearchService;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Updates the semantic data project name when the project is renamed.
 */
@Service
public class ProjectNameUpdatedSemanticDataUpdater {

    private final IProjectSemanticDataSearchService projectSemanticDataSearchService;

    private final IEditingContextSearchService editingContextSearchService;

    private final IEditingContextPersistenceService editingContextPersistenceService;

    public ProjectNameUpdatedSemanticDataUpdater(IProjectSemanticDataSearchService projectSemanticDataSearchService, IEditingContextSearchService editingContextSearchService,
            IEditingContextPersistenceService editingContextPersistenceService) {
        this.projectSemanticDataSearchService = Objects.requireNonNull(projectSemanticDataSearchService);
        this.editingContextSearchService = Objects.requireNonNull(editingContextSearchService);
        this.editingContextPersistenceService = Objects.requireNonNull(editingContextPersistenceService);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    public void onProjectNameUpdatedEvent(ProjectNameUpdatedEvent event) {
        var projectId = event.project().getId();
        var optionalEditingContext = this.projectSemanticDataSearchService.findByProjectId(AggregateReference.to(projectId)).map(ProjectSemanticData::getSemanticData).map(AggregateReference::getId)
                .map(UUID::toString).flatMap(this.editingContextSearchService::findById);

        if (optionalEditingContext.isPresent() && optionalEditingContext.get() instanceof IEMFEditingContext emfEditingContext) {
            String newName = event.project().getName();
            var resources = emfEditingContext.getDomain().getResourceSet().getResources();
            if (!resources.isEmpty()) {
                var rootResource = resources.get(0);
                rootResource.eAdapters().stream().filter(ResourceMetadataAdapter.class::isInstance).map(ResourceMetadataAdapter.class::cast).findFirst().ifPresent(adapter -> adapter.setName(newName));

                this.editingContextPersistenceService.persist(event.causedBy(), emfEditingContext);
            }
        }
    }
}
