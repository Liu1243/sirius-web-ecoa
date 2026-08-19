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

import edtinterface.EDTInterfaceFactory;
import edtproject.Step0;
import edttype.EDTDataType;
import edttype.Library;
import org.eclipse.emf.common.util.EList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import technology.ecoa.interface_._2.*;

/**
 * Converts ECOA ServiceDefinition XML to EDT ServiceDefinition objects.
 * Based on the original ServiceDefinitionImportConverter from com.dassault.edt.import.
 */
public class ServiceDefinitionImportConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceDefinitionImportConverter.class);
    private static final EDTInterfaceFactory EDTSERVICEFACTORY = EDTInterfaceFactory.eINSTANCE;

    private ServiceDefinitionImportConverter() {
        // Utility class
    }

    /**
     * Create an EDT ServiceDefinition from an ECOA ServiceDefinition.
     *
     * @param ecoaServiceDefinition the ECOA ServiceDefinition
     * @param fileName              the file name
     * @param step0                 the Step0 containing types
     * @return the EDT ServiceDefinition
     */
    public static edtinterface.ServiceDefinition createEDTServiceDefinition(
            ServiceDefinition ecoaServiceDefinition, String fileName, Step0 step0) {

        var edtServiceDefinition = EDTSERVICEFACTORY.createServiceDefinition();

        // Set name from file name
        edtServiceDefinition.setName(TypesImportConverter.getObjectName(fileName, ".interface.xml"));

        // Convert operations
        var operations = ecoaServiceDefinition.getOperations();

        LOGGER.info("[SVC-IMPORT] '{}': ECOAOps={}, data={}, event={}, rr={}",
                edtServiceDefinition.getName(),
                operations == null ? "NULL" : "OK",
                operations == null ? 0 : operations.getData().size(),
                operations == null ? 0 : operations.getEvent().size(),
                operations == null ? 0 : operations.getRequestresponse().size());

        if (operations != null) {
            for (Data ecoaData : operations.getData()) {
                edtServiceDefinition.getOperations().add(createEDTData(step0, ecoaData));
            }

            for (Event ecoaEvent : operations.getEvent()) {
                LOGGER.info("[SVC-IMPORT] Event '{}': inputs={}", ecoaEvent.getName(), ecoaEvent.getInput().size());
                for (Parameter p : ecoaEvent.getInput()) {
                    LOGGER.info("[SVC-IMPORT]   param '{}' type='{}'", p.getName(), p.getType());
                }
                edtServiceDefinition.getOperations().add(createEDTEvent(step0, ecoaEvent));
            }

            for (RequestResponse ecoaRequestResponse : operations.getRequestresponse()) {
                edtServiceDefinition.getOperations().add(createEDTRequestResponse(step0, ecoaRequestResponse));
            }
        }

        LOGGER.info("[SVC-IMPORT] '{}' EDT ops result count: {}",
                edtServiceDefinition.getName(), edtServiceDefinition.getOperations().size());

        // Associate used libraries
        EList<UseType> usedLibraries = ecoaServiceDefinition.getUse();
        for (UseType useType : usedLibraries) {
            String libraryName = useType.getLibrary();
            if ("ECOA".equals(libraryName)) {
                continue;
            }
            Library found = step0.findLibrary(libraryName);
            if (found != null) {
                edtServiceDefinition.getUsedLibraries().add(found);
            }
        }

        return edtServiceDefinition;
    }

    private static edtinterface.Data createEDTData(Step0 step0, Data ecoaData) {
        var edtData = EDTSERVICEFACTORY.createData();
        edtData.setName(ecoaData.getName());
        edtData.setComment(ecoaData.getComment());

        String typeName = ecoaData.getType();
        EDTDataType edtType = TypesImportConverter.findEDTDataTypeForNonTypes(step0, typeName);
        if (edtType != null) {
            edtData.setType(edtType);
        }

        return edtData;
    }

    private static edtinterface.Event createEDTEvent(Step0 step0, Event ecoaEvent) {
        var edtEvent = EDTSERVICEFACTORY.createEvent();
        edtEvent.setName(ecoaEvent.getName());
        edtEvent.setComment(ecoaEvent.getComment());
        edtEvent.setDirection(ecoaEvent.getDirection());

        for (Parameter param : ecoaEvent.getInput()) {
            edtEvent.getInput().add(createEDTParameter(step0, param));
        }

        return edtEvent;
    }

    private static edtinterface.RequestResponse createEDTRequestResponse(Step0 step0,
            RequestResponse ecoaRequestResponse) {
        var edtRequestResponse = EDTSERVICEFACTORY.createRequestResponse();
        edtRequestResponse.setName(ecoaRequestResponse.getName());
        edtRequestResponse.setComment(ecoaRequestResponse.getComment());

        for (Parameter param : ecoaRequestResponse.getInput()) {
            edtRequestResponse.getInput().add(createEDTParameter(step0, param));
        }

        for (Parameter param : ecoaRequestResponse.getOutput()) {
            edtRequestResponse.getOutput().add(createEDTParameter(step0, param));
        }

        return edtRequestResponse;
    }

    private static edtinterface.Parameter createEDTParameter(Step0 step0, Parameter ecoaParameter) {
        var edtParameter = EDTSERVICEFACTORY.createParameter();
        edtParameter.setName(ecoaParameter.getName());

        String typeName = ecoaParameter.getType();
        EDTDataType edtType = TypesImportConverter.findEDTDataTypeForNonTypes(step0, typeName);
        if (edtType != null) {
            edtParameter.setType(edtType);
        }

        return edtParameter;
    }
}
