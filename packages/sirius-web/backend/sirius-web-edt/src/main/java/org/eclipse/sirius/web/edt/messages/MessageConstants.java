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
 * This class is used to hold all the keys of the internationalization messages.
 *
 * @author tgiraudet
 */
public final class MessageConstants {

    public static final String CORE_PROPERTIES = "CORE_PROPERTIES";
    
    // Step0 - Types
    public static final String TYPES = "TYPES";
    public static final String BASIC_TYPES = "BASIC_TYPES";
    public static final String ECOA_PREDEFINED_TYPES = "ECOA_PREDEFINED_TYPES";
    public static final String LIBRARIES = "LIBRARIES";
    
    // Step1 - Services
    public static final String SERVICES = "SERVICES";
    public static final String SERVICE_DEFINITIONS = "SERVICE_DEFINITIONS";
    
    // Step2 - Component Definitions
    public static final String COMPONENT_DEFINITIONS = "COMPONENT_DEFINITIONS";
    public static final String ALL_COMPONENT_DEFINITIONS = "ALL_COMPONENT_DEFINITIONS";
    
    // Step3 - Initial Assembly
    public static final String INITIAL_ASSEMBLY = "INITIAL_ASSEMBLY";
    public static final String COMPOSITE = "COMPOSITE";
    
    // Step4 - Component Implementations
    public static final String COMPONENT_IMPLEMENTATIONS = "COMPONENT_IMPLEMENTATIONS";
    public static final String ALL_COMPONENT_IMPLEMENTATIONS = "ALL_COMPONENT_IMPLEMENTATIONS";
    public static final String COMPONENT_DEFINITION = "COMPONENT_DEFINITION";
    public static final String USED_LIBRARIES = "USED_LIBRARIES";
    
    // Step5 - Integration
    public static final String INTEGRATION = "INTEGRATION";
    public static final String LOGICAL_SYSTEM = "LOGICAL_SYSTEM";
    public static final String DEPLOYMENT = "DEPLOYMENT";

    // Logical Computing Node
    public static final String PROTECTION_DOMAIN_LINKS = "PROTECTION_DOMAIN_LINKS";


    private MessageConstants() {
        // Prevent instantiation
    }
}
