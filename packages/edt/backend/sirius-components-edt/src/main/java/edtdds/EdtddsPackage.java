/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package edtdds;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;

public interface EdtddsPackage extends EPackage {

    String eNAME = "edtdds";
    String eNS_URI = "edtdds";
    String eNS_PREFIX = "edtdds";

    EdtddsPackage eINSTANCE = edtdds.impl.EdtddsPackageImpl.init();

    // DDSBinding class
    int DDS_BINDING = 0;
    int DDS_BINDING__NAME      = 0;
    int DDS_BINDING__DOMAIN_ID = 1;
    int DDS_BINDING__TOPIC_NAME = 2;
    int DDS_BINDING_FEATURE_COUNT = 3;
    int DDS_BINDING_OPERATION_COUNT = 0;

    EClass getDDSBinding();
    EAttribute getDDSBinding_Name();
    EAttribute getDDSBinding_DomainId();
    EAttribute getDDSBinding_TopicName();

    EdtddsFactory getEdtddsFactory();

    interface Literals {
        EClass DDS_BINDING = eINSTANCE.getDDSBinding();
        EAttribute DDS_BINDING__NAME       = eINSTANCE.getDDSBinding_Name();
        EAttribute DDS_BINDING__DOMAIN_ID  = eINSTANCE.getDDSBinding_DomainId();
        EAttribute DDS_BINDING__TOPIC_NAME = eINSTANCE.getDDSBinding_TopicName();
    }

} // EdtddsPackage
