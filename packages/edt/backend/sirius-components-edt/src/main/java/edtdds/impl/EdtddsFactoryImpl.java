/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package edtdds.impl;

import edtdds.DDSBinding;
import edtdds.EdtddsFactory;
import edtdds.EdtddsPackage;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.impl.EFactoryImpl;
import org.eclipse.emf.ecore.plugin.EcorePlugin;

public class EdtddsFactoryImpl extends EFactoryImpl implements EdtddsFactory {

    public static EdtddsFactory init() {
        try {
            EdtddsFactory f = (EdtddsFactory) EPackage.Registry.INSTANCE.getEFactory(EdtddsPackage.eNS_URI);
            if (f != null) return f;
        } catch (Exception e) { EcorePlugin.INSTANCE.log(e); }
        return new EdtddsFactoryImpl();
    }

    @Override
    public EObject create(EClass eClass) {
        switch (eClass.getClassifierID()) {
            case EdtddsPackage.DDS_BINDING: return createDDSBinding();
            default: throw new IllegalArgumentException("The class '" + eClass.getName() + "' is not a valid classifier");
        }
    }

    @Override public DDSBinding createDDSBinding() { return new DDSBindingImpl(); }
    @Override public EdtddsPackage getEdtddsPackage() { return (EdtddsPackage) getEPackage(); }

} // EdtddsFactoryImpl
