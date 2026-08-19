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

import org.eclipse.emf.common.util.EList;

import edtimplementation.EventReceived;
import edtimplementation.EventSent;
import edtimplementation.ModuleOperation;
import edtimplementation.Parameter;
import edtimplementation.RequestReceived;
import edtimplementation.RequestSent;
import edtimplementation.VersionedDataRead;
import edtimplementation.VersionedDataWritten;
import edttype.EDTDataType;
import technology.ecoa.implementation._2.impFactory;

/**
 * Converts EDT ModuleType operations to ECOA OperationsType elements.
 * Based on the original ComponentImplementationOperationsExportConverter from edt-tmp.
 */
public class ComponentImplementationOperationsExportConverter {

    private static final impFactory IMPFACTORY = impFactory.eINSTANCE;

    private ComponentImplementationOperationsExportConverter() {
        // Utility class
    }

    /**
     * Convert a single EDT ModuleOperation to its ECOA counterpart and add it to the given OperationsType.
     */
    static void recreateECOAOperations(technology.ecoa.implementation._2.OperationsType ecoaOperationsType,
            ModuleOperation edtModuleOperation) {
        if (edtModuleOperation instanceof VersionedDataRead op) {
            ecoaOperationsType.getDataRead().add(recreateECOADataRead(op));
        } else if (edtModuleOperation instanceof VersionedDataWritten op) {
            ecoaOperationsType.getDataWritten().add(recreateECOADataWritten(op));
        } else if (edtModuleOperation instanceof EventSent op) {
            ecoaOperationsType.getEventSent().add(recreateECOAEventSent(op));
        } else if (edtModuleOperation instanceof EventReceived op) {
            ecoaOperationsType.getEventReceived().add(recreateECOAEventReceived(op));
        } else if (edtModuleOperation instanceof RequestSent op) {
            ecoaOperationsType.getRequestSent().add(recreateECOARequestSent(op));
        } else if (edtModuleOperation instanceof RequestReceived op) {
            ecoaOperationsType.getRequestReceived().add(recreateECOARequestReceived(op));
        }
    }

    private static technology.ecoa.implementation._2.DataReadType recreateECOADataRead(VersionedDataRead edtDataRead) {
        var ecoaDataRead = IMPFACTORY.createDataReadType();
        ecoaDataRead.setName(edtDataRead.getName());
        if (edtDataRead.isSetMaxVersions()) {
            ecoaDataRead.setMaxVersions(edtDataRead.getMaxVersions());
        }
        if (edtDataRead.isSetNotifying()) {
            ecoaDataRead.setNotifying(edtDataRead.isNotifying());
        }
        EDTDataType type = edtDataRead.getType();
        if (type != null) {
            ecoaDataRead.setType(TypesExportConverter.recreateDataTypeNameForNonTypes(type));
        }
        return ecoaDataRead;
    }

    private static technology.ecoa.implementation._2.DataWrittenType recreateECOADataWritten(VersionedDataWritten edtDataWritten) {
        var ecoaDataWritten = IMPFACTORY.createDataWrittenType();
        ecoaDataWritten.setName(edtDataWritten.getName());
        if (edtDataWritten.isSetMaxVersions()) {
            ecoaDataWritten.setMaxVersions(edtDataWritten.getMaxVersions());
        }
        if (edtDataWritten.isSetWriteOnly()) {
            ecoaDataWritten.setWriteOnly(edtDataWritten.isWriteOnly());
        }
        EDTDataType type = edtDataWritten.getType();
        if (type != null) {
            ecoaDataWritten.setType(TypesExportConverter.recreateDataTypeNameForNonTypes(type));
        }
        return ecoaDataWritten;
    }

    static technology.ecoa.implementation._2.Event recreateECOAEventSent(EventSent edtEventSent) {
        var ecoaEventSent = IMPFACTORY.createEvent();
        ecoaEventSent.setName(edtEventSent.getName());
        EList<Parameter> edtInputs = edtEventSent.getInput();
        for (Parameter edtParameter : edtInputs) {
            ecoaEventSent.getInput().add(recreateECOAParameter(edtParameter));
        }
        return ecoaEventSent;
    }

    private static technology.ecoa.implementation._2.EventReceivedType recreateECOAEventReceived(EventReceived edtEventReceived) {
        var ecoaEventReceived = IMPFACTORY.createEventReceivedType();
        ecoaEventReceived.setName(edtEventReceived.getName());
        EList<Parameter> edtInputs = edtEventReceived.getInput();
        for (Parameter edtParameter : edtInputs) {
            ecoaEventReceived.getInput().add(recreateECOAParameter(edtParameter));
        }
        return ecoaEventReceived;
    }

    private static technology.ecoa.implementation._2.RequestSentType recreateECOARequestSent(RequestSent edtRequestSent) {
        var ecoaRequestSent = IMPFACTORY.createRequestSentType();
        ecoaRequestSent.setName(edtRequestSent.getName());
        if (edtRequestSent.isSetMaxConcurrentRequests()) {
            ecoaRequestSent.setMaxConcurrentRequests(edtRequestSent.getMaxConcurrentRequests());
        }
        ecoaRequestSent.setIsSynchronous(edtRequestSent.isIsSynchronous());
        if (edtRequestSent.isSetTimeout()) {
            ecoaRequestSent.setTimeout(edtRequestSent.getTimeout());
        }
        for (Parameter p : edtRequestSent.getInput()) {
            ecoaRequestSent.getInput().add(recreateECOAParameter(p));
        }
        for (Parameter p : edtRequestSent.getOutput()) {
            ecoaRequestSent.getOutput().add(recreateECOAParameter(p));
        }
        return ecoaRequestSent;
    }

    private static technology.ecoa.implementation._2.RequestReceivedType recreateECOARequestReceived(RequestReceived edtRequestReceived) {
        var ecoaRequestReceived = IMPFACTORY.createRequestReceivedType();
        ecoaRequestReceived.setName(edtRequestReceived.getName());
        if (edtRequestReceived.isSetMaxConcurrentRequests()) {
            ecoaRequestReceived.setMaxConcurrentRequests(edtRequestReceived.getMaxConcurrentRequests());
        }
        for (Parameter p : edtRequestReceived.getInput()) {
            ecoaRequestReceived.getInput().add(recreateECOAParameter(p));
        }
        for (Parameter p : edtRequestReceived.getOutput()) {
            ecoaRequestReceived.getOutput().add(recreateECOAParameter(p));
        }
        return ecoaRequestReceived;
    }

    static technology.ecoa.implementation._2.Parameter recreateECOAParameter(Parameter edtParameter) {
        var ecoaParameter = IMPFACTORY.createParameter();
        ecoaParameter.setName(edtParameter.getName());
        EDTDataType type = edtParameter.getType();
        if (type != null) {
            ecoaParameter.setType(TypesExportConverter.recreateDataTypeNameForNonTypes(type));
        }
        return ecoaParameter;
    }
}
