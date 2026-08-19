/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package edttcp;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;

/**
 * The <b>Package</b> for the edttcp model.
 */
public interface EdttcpPackage extends EPackage {

    String eNAME = "edttcp";
    String eNS_URI = "edttcp";
    String eNS_PREFIX = "edttcp";

    EdttcpPackage eINSTANCE = edttcp.impl.EdttcpPackageImpl.init();

    // TCPBinding class
    int TCP_BINDING = 0;
    int TCP_BINDING__NAME = 0;
    int TCP_BINDING__PLATFORM = 1;
    int TCP_BINDING_FEATURE_COUNT = 2;
    int TCP_BINDING_OPERATION_COUNT = 0;

    // TCPPlatform class
    int TCP_PLATFORM = 1;
    int TCP_PLATFORM__NAME = 0;
    int TCP_PLATFORM__ADDRESS = 1;
    int TCP_PLATFORM__PORT = 2;
    int TCP_PLATFORM_FEATURE_COUNT = 3;
    int TCP_PLATFORM_OPERATION_COUNT = 0;

    EClass getTCPBinding();
    EAttribute getTCPBinding_Name();
    EReference getTCPBinding_Platform();

    EClass getTCPPlatform();
    EAttribute getTCPPlatform_Name();
    EAttribute getTCPPlatform_Address();
    EAttribute getTCPPlatform_Port();

    EdttcpFactory getEdttcpFactory();

    interface Literals {
        EClass TCP_BINDING = eINSTANCE.getTCPBinding();
        EAttribute TCP_BINDING__NAME = eINSTANCE.getTCPBinding_Name();
        EReference TCP_BINDING__PLATFORM = eINSTANCE.getTCPBinding_Platform();

        EClass TCP_PLATFORM = eINSTANCE.getTCPPlatform();
        EAttribute TCP_PLATFORM__NAME = eINSTANCE.getTCPPlatform_Name();
        EAttribute TCP_PLATFORM__ADDRESS = eINSTANCE.getTCPPlatform_Address();
        EAttribute TCP_PLATFORM__PORT = eINSTANCE.getTCPPlatform_Port();
    }

} // EdttcpPackage
