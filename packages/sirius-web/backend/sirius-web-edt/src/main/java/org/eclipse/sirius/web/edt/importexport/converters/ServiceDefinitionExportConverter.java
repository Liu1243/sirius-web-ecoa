/*******************************************************************************
 * Copyright (c) 2024 Dassault Aviation.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Dassault Aviation - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.web.edt.importexport.converters;

import edtinterface.OperationType;
import edttype.EDTDataType;
import edttype.Library;
import org.eclipse.emf.common.util.EList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import technology.ecoa.interface_._2.*;

/**
 * Converts EDT ServiceDefinition objects to ECOA Interface XML format.
 * Based on the original ServiceDefinitionExportConverter from edt-tmp.
 */
public class ServiceDefinitionExportConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceDefinitionExportConverter.class);
    private static final interFactory SERVICEFACTORY = interFactory.eINSTANCE;

    private ServiceDefinitionExportConverter() {
        // Utility class
    }

    /**
     * Convert EDT ServiceDefinition to ECOA ServiceDefinition in DocumentRoot.
     *
     * @param edtServiceDefinition the EDT ServiceDefinition to convert
     * @return ECOA DocumentRoot containing the ServiceDefinition
     */
    public static DocumentRoot recreateServiceDefinition(edtinterface.ServiceDefinition edtServiceDefinition) {
        var ecoaServiceDefinition = SERVICEFACTORY.createServiceDefinition();

        // Diagnostic: check object state and eClass
        LOGGER.info("[SVC-EXPORT] Exporting '{}': EDT ops count={}, eClass={}, eContainer={}",
                edtServiceDefinition.getName(),
                edtServiceDefinition.getOperations().size(),
                edtServiceDefinition.eClass().getName(),
                edtServiceDefinition.eContainer() == null ? "NULL" : edtServiceDefinition.eContainer().eClass().getName());

        // Also log via eGet to diagnose any override issues
        Object rawOps = edtServiceDefinition.eGet(edtinterface.EDTInterfacePackage.eINSTANCE.getServiceDefinition_Operations());
        LOGGER.info("[SVC-EXPORT] '{}' via eGet: rawOps type={}, size={}",
                edtServiceDefinition.getName(),
                rawOps == null ? "NULL" : rawOps.getClass().getSimpleName(),
                rawOps instanceof java.util.Collection ? ((java.util.Collection<?>) rawOps).size() : "N/A");

        // Set used libraries
        EList<Library> usedLibraries = edtServiceDefinition.getUsedLibraries();
        usedLibraries.forEach(lib -> ecoaServiceDefinition.getUse().add(recreateUseType(lib)));

        // Create operations
        EList<OperationType> edtOperations = edtServiceDefinition.getOperations();
        var operations = SERVICEFACTORY.createOperations();

        for (OperationType operationType : edtOperations) {
            if (operationType instanceof edtinterface.Data op) {
                LOGGER.info("[SVC-EXPORT]   Data '{}'", op.getName());
                operations.getData().add(recreateData(op));
            } else if (operationType instanceof edtinterface.Event op) {
                LOGGER.info("[SVC-EXPORT]   Event '{}': inputs={}", op.getName(), op.getInput().size());
                for (edtinterface.Parameter p : op.getInput()) {
                    LOGGER.info("[SVC-EXPORT]     param '{}' type='{}'", p.getName(), p.getType() != null ? p.getType().getName() : "NULL");
                }
                operations.getEvent().add(recreateEvent(op));
            } else if (operationType instanceof edtinterface.RequestResponse op) {
                LOGGER.info("[SVC-EXPORT]   RequestResponse '{}'", op.getName());
                operations.getRequestresponse().add(recreateRequestResponse(op));
            }
        }
        ecoaServiceDefinition.setOperations(operations);

        LOGGER.info("[SVC-EXPORT] '{}': ECOA event count={}, data count={}",
                edtServiceDefinition.getName(), operations.getEvent().size(), operations.getData().size());

        var documentRoot = SERVICEFACTORY.createDocumentRoot();
        documentRoot.setServiceDefinition(ecoaServiceDefinition);
        return documentRoot;
    }

    /**
     * Convert EDT Library to ECOA UseType.
     */
    public static UseType recreateUseType(Library usedLibrary) {
        var useType = SERVICEFACTORY.createUseType();
        useType.setLibrary(usedLibrary.getName());
        return useType;
    }

    private static Data recreateData(edtinterface.Data edtData) {
        var ecoaData = SERVICEFACTORY.createData();
        recreateOperation(ecoaData, edtData);
        EDTDataType type = edtData.getType();
        if (type != null) {
            ecoaData.setType(TypesExportConverter.recreateDataTypeNameForNonTypes(type));
        }
        return ecoaData;
    }

    private static Event recreateEvent(edtinterface.Event edtEvent) {
        var ecoaEvent = SERVICEFACTORY.createEvent();
        recreateOperation(ecoaEvent, edtEvent);
        ecoaEvent.setDirection(edtEvent.getDirection());

        EList<edtinterface.Parameter> inputs = edtEvent.getInput();
        inputs.forEach(param -> ecoaEvent.getInput().add(recreateParameter(param)));

        return ecoaEvent;
    }

    private static RequestResponse recreateRequestResponse(edtinterface.RequestResponse edtRequest) {
        var ecoaRequest = SERVICEFACTORY.createRequestResponse();
        recreateOperation(ecoaRequest, edtRequest);

        EList<edtinterface.Parameter> inputs = edtRequest.getInput();
        inputs.forEach(param -> ecoaRequest.getInput().add(recreateParameter(param)));

        EList<edtinterface.Parameter> outputs = edtRequest.getOutput();
        outputs.forEach(param -> ecoaRequest.getOutput().add(recreateParameter(param)));

        return ecoaRequest;
    }

    private static Parameter recreateParameter(edtinterface.Parameter edtParameter) {
        var ecoaParameter = SERVICEFACTORY.createParameter();
        ecoaParameter.setName(edtParameter.getName());
        EDTDataType type = edtParameter.getType();
        if (type != null) {
            ecoaParameter.setType(TypesExportConverter.recreateDataTypeNameForNonTypes(type));
        }
        return ecoaParameter;
    }

    private static void recreateOperation(Operation ecoa, OperationType edt) {
        ecoa.setName(edt.getName());
        if (edt.getComment() != null && !edt.getComment().isBlank()) {
            ecoa.setComment(edt.getComment());
        }
    }
}
