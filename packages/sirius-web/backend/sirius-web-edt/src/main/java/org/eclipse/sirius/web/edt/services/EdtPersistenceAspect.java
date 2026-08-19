package org.eclipse.sirius.web.edt.services;

import java.util.Objects;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.web.edt.services.api.IEdtCapableEditingContextPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ensures EDT-specific link rebinding happens right before persistence as well.
 */
@Aspect
@Component
public class EdtPersistenceAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdtPersistenceAspect.class);

    private final IEdtCapableEditingContextPredicate edtCapableEditingContextPredicate;

    private final EdtServiceDefinitionRepairService edtServiceDefinitionRepairService;

    public EdtPersistenceAspect(IEdtCapableEditingContextPredicate edtCapableEditingContextPredicate,
            EdtServiceDefinitionRepairService edtServiceDefinitionRepairService) {
        this.edtCapableEditingContextPredicate = Objects.requireNonNull(edtCapableEditingContextPredicate);
        this.edtServiceDefinitionRepairService = Objects.requireNonNull(edtServiceDefinitionRepairService);
    }

    @Around("execution(* org.eclipse.sirius.components.core.api.IEditingContextPersistenceService.persist(..)) && args(cause, editingContext)")
    public Object prepareEdtContextBeforePersist(ProceedingJoinPoint joinPoint, Object cause, IEditingContext editingContext) throws Throwable {
        if (editingContext instanceof IEMFEditingContext emfEditingContext
                && this.edtCapableEditingContextPredicate.test(editingContext.getId())) {
            LOGGER.info("[EDT-PERSIST] Preparing EDT editing context {} before persistence", editingContext.getId());
            this.edtServiceDefinitionRepairService.prepareForPersistence(editingContext.getId(),
                    emfEditingContext.getDomain().getResourceSet());
        }

        return joinPoint.proceed(new Object[] { cause, editingContext });
    }
}
