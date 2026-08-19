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

/**
 * Interface of the EDT message service.
 *
 * @author tgiraudet
 */
public interface IEdtMessageService {

    String coreProperties();
    
    // Step0 - Types
    String types();
    String basicTypes();
    String ecoaPredefinedTypes();
    String libraries();
    
    // Step1 - Services
    String services();
    String serviceDefinitions();
    
    // Step2 - Component Definitions
    String componentDefinitions();
    String allComponentDefinitions();
    
    // Step3 - Initial Assembly
    String initialAssembly();
    String composite();
    
    // Step4 - Component Implementations
    String componentImplementations();
    String allComponentImplementations();
    String componentDefinition();
    String usedLibraries();
    
    // Step5 - Integration
    String integration();
    String logicalSystem();
    String deployment();

    // Logical Computing Node
    String protectionDomainLinks();

    /**
     * Implementation which does nothing, used for mocks in unit tests.
     *
     * @author tgiraudet
     */
    class NoOp implements IEdtMessageService {

        @Override
        public String coreProperties() {
            return "";
        }

        @Override
        public String types() {
            return "";
        }

        @Override
        public String basicTypes() {
            return "";
        }

        @Override
        public String ecoaPredefinedTypes() {
            return "";
        }

        @Override
        public String libraries() {
            return "";
        }

        @Override
        public String services() {
            return "";
        }

        @Override
        public String serviceDefinitions() {
            return "";
        }

        @Override
        public String componentDefinitions() {
            return "";
        }

        @Override
        public String allComponentDefinitions() {
            return "";
        }

        @Override
        public String initialAssembly() {
            return "";
        }

        @Override
        public String composite() {
            return "";
        }

        @Override
        public String componentImplementations() {
            return "";
        }

        @Override
        public String allComponentImplementations() {
            return "";
        }

        @Override
        public String componentDefinition() {
            return "";
        }

        @Override
        public String usedLibraries() {
            return "";
        }

        @Override
        public String integration() {
            return "";
        }

        @Override
        public String logicalSystem() {
            return "";
        }

        @Override
        public String deployment() {
            return "";
        }

        @Override
        public String protectionDomainLinks() {
            return "";
        }
    }
}
