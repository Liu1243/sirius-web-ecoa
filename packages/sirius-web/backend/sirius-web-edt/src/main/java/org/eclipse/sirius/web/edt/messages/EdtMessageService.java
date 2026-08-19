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
package org.eclipse.sirius.web.edt.messages;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Service;

/**
 * Implementation of the EDT message service.
 *
 * @author tgiraudet
 */
@Service
public class EdtMessageService implements IEdtMessageService {

    private final MessageSourceAccessor messageSourceAccessor;

    public EdtMessageService(@Qualifier("edtMessageSourceAccessor") MessageSourceAccessor messageSourceAccessor) {
        this.messageSourceAccessor = Objects.requireNonNull(messageSourceAccessor);
    }

    @Override
    public String coreProperties() {
        return messageSourceAccessor.getMessage(MessageConstants.CORE_PROPERTIES);
    }

    @Override
    public String types() {
        return messageSourceAccessor.getMessage(MessageConstants.TYPES);
    }

    @Override
    public String basicTypes() {
        return messageSourceAccessor.getMessage(MessageConstants.BASIC_TYPES);
    }

    @Override
    public String ecoaPredefinedTypes() {
        return messageSourceAccessor.getMessage(MessageConstants.ECOA_PREDEFINED_TYPES);
    }

    @Override
    public String libraries() {
        return messageSourceAccessor.getMessage(MessageConstants.LIBRARIES);
    }

    @Override
    public String services() {
        return messageSourceAccessor.getMessage(MessageConstants.SERVICES);
    }

    @Override
    public String serviceDefinitions() {
        return messageSourceAccessor.getMessage(MessageConstants.SERVICE_DEFINITIONS);
    }

    @Override
    public String componentDefinitions() {
        return messageSourceAccessor.getMessage(MessageConstants.COMPONENT_DEFINITIONS);
    }

    @Override
    public String allComponentDefinitions() {
        return messageSourceAccessor.getMessage(MessageConstants.ALL_COMPONENT_DEFINITIONS);
    }

    @Override
    public String initialAssembly() {
        return messageSourceAccessor.getMessage(MessageConstants.INITIAL_ASSEMBLY);
    }

    @Override
    public String composite() {
        return messageSourceAccessor.getMessage(MessageConstants.COMPOSITE);
    }

    @Override
    public String componentImplementations() {
        return messageSourceAccessor.getMessage(MessageConstants.COMPONENT_IMPLEMENTATIONS);
    }

    @Override
    public String allComponentImplementations() {
        return messageSourceAccessor.getMessage(MessageConstants.ALL_COMPONENT_IMPLEMENTATIONS);
    }

    @Override
    public String componentDefinition() {
        return messageSourceAccessor.getMessage(MessageConstants.COMPONENT_DEFINITION);
    }

    @Override
    public String usedLibraries() {
        return messageSourceAccessor.getMessage(MessageConstants.USED_LIBRARIES);
    }

    @Override
    public String integration() {
        return messageSourceAccessor.getMessage(MessageConstants.INTEGRATION);
    }

    @Override
    public String logicalSystem() {
        return messageSourceAccessor.getMessage(MessageConstants.LOGICAL_SYSTEM);
    }

    @Override
    public String deployment() {
        return messageSourceAccessor.getMessage(MessageConstants.DEPLOYMENT);
    }

    @Override
    public String protectionDomainLinks() {
        return messageSourceAccessor.getMessage(MessageConstants.PROTECTION_DOMAIN_LINKS);
    }
}
