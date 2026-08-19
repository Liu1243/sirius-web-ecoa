/*******************************************************************************
 * Copyright (c) 2025 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.web.edt.services;

import edtbin.EdtbinPackage;
import edtdeployment.EdtdeploymentPackage;
import edtimplementation.EdtimplementationPackage;
import edtinterface.EDTInterfacePackage;
import edtlogical.EdtlogicalPackage;
import edtproject.EDTProjectPackage;
import edtqos.EdtqosPackage;
import edttype.EDTTypePackage;
import edtdds.EdtddsPackage;
import edttcp.EdttcpPackage;
import edtudp.EdtudpPackage;
import edtuid.EdtuidPackage;
import org.eclipse.emf.ecore.xml.type.XMLTypePackage;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextPersistenceService;
import org.eclipse.sirius.components.core.api.IEditingContextProcessor;
import org.eclipse.sirius.web.application.editingcontext.EditingContext;
import org.eclipse.sirius.web.edt.services.api.IEdtCapableEditingContextPredicate;
import org.eclipse.sirius.web.edt.services.api.IEdtViewProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Used to initialize the editing context of an edt project.
 *
 * @author managerial
 */
@Service
public class EdtEditingContextInitializer implements IEditingContextProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdtEditingContextInitializer.class);

    private final IEdtViewProvider edtViewProvider;

    private final IEdtCapableEditingContextPredicate edtCapableEditingContextPredicate;

    private final EdtServiceDefinitionRepairService edtServiceDefinitionRepairService;

    private final IEditingContextPersistenceService editingContextPersistenceService;

    public EdtEditingContextInitializer(IEdtViewProvider edtViewProvider,
            IEdtCapableEditingContextPredicate edtEditingContextPredicate,
            EdtServiceDefinitionRepairService edtServiceDefinitionRepairService,
            IEditingContextPersistenceService editingContextPersistenceService) {
        this.edtViewProvider = Objects.requireNonNull(edtViewProvider);
        this.edtCapableEditingContextPredicate = Objects.requireNonNull(edtEditingContextPredicate);
        this.edtServiceDefinitionRepairService = Objects.requireNonNull(edtServiceDefinitionRepairService);
        this.editingContextPersistenceService = Objects.requireNonNull(editingContextPersistenceService);
    }

    @Override
    public void preProcess(IEditingContext editingContext) {
        if (this.edtCapableEditingContextPredicate.test(editingContext.getId()) && editingContext instanceof EditingContext emfEditingContext) {
            LOGGER.info("[EDT-INIT] Pre-processing EDT editing context {}", editingContext.getId());
            var packageRegistry = emfEditingContext.getDomain().getResourceSet().getPackageRegistry();
            packageRegistry.put(EDTProjectPackage.eNS_URI, EDTProjectPackage.eINSTANCE);
            packageRegistry.put(EDTInterfacePackage.eNS_URI, EDTInterfacePackage.eINSTANCE);
            packageRegistry.put(EDTTypePackage.eNS_URI, EDTTypePackage.eINSTANCE);
            packageRegistry.put(EdtdeploymentPackage.eNS_URI, EdtdeploymentPackage.eINSTANCE);
            packageRegistry.put(EdtimplementationPackage.eNS_URI, EdtimplementationPackage.eINSTANCE);
            packageRegistry.put(EdtlogicalPackage.eNS_URI, EdtlogicalPackage.eINSTANCE);
            packageRegistry.put(EdtqosPackage.eNS_URI, EdtqosPackage.eINSTANCE);
            packageRegistry.put(EdtbinPackage.eNS_URI, EdtbinPackage.eINSTANCE);
            packageRegistry.put(EdtddsPackage.eNS_URI, EdtddsPackage.eINSTANCE);
            packageRegistry.put(EdttcpPackage.eNS_URI, EdttcpPackage.eINSTANCE);
            packageRegistry.put(EdtudpPackage.eNS_URI, EdtudpPackage.eINSTANCE);
            packageRegistry.put(EdtuidPackage.eNS_URI, EdtuidPackage.eINSTANCE);
            packageRegistry.put(XMLTypePackage.eNS_URI, XMLTypePackage.eINSTANCE);

            emfEditingContext.getViews().add(this.edtViewProvider.create());
        } else {
            LOGGER.debug("[EDT-INIT] Skipping editing context {} because it is not EDT-capable", editingContext.getId());
        }
    }

    @Override
    public void postProcess(IEditingContext editingContext) {
        if (this.edtCapableEditingContextPredicate.test(editingContext.getId()) && editingContext instanceof EditingContext emfEditingContext) {
            LOGGER.info("[EDT-INIT] Post-processing EDT editing context {}", editingContext.getId());
            boolean repaired = this.edtServiceDefinitionRepairService.repairIfNeeded(editingContext.getId(),
                    emfEditingContext.getDomain().getResourceSet());
            if (repaired) {
                LOGGER.info("[EDT-INIT] Persisting repaired EDT editing context {}", editingContext.getId());
                this.editingContextPersistenceService.persist(new org.eclipse.sirius.components.events.ICause.NoOp(), editingContext);
            }
        }
    }
}
