/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package edttcp.util;

import edttcp.EdttcpPackage;
import edttcp.TCPBinding;
import edttcp.TCPPlatform;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.notify.impl.AdapterFactoryImpl;
import org.eclipse.emf.ecore.EObject;

public class EdttcpAdapterFactory extends AdapterFactoryImpl {

    protected static EdttcpPackage modelPackage;

    public EdttcpAdapterFactory() {
        if (modelPackage == null) {
            modelPackage = EdttcpPackage.eINSTANCE;
        }
    }

    @Override
    public boolean isFactoryForType(Object object) {
        if (object == modelPackage) return true;
        if (object instanceof EObject) return ((EObject) object).eClass().getEPackage() == modelPackage;
        return false;
    }

    protected EdttcpSwitch<Adapter> modelSwitch = new EdttcpSwitch<>() {
        @Override public Adapter caseTCPBinding(TCPBinding object)   { return createTCPBindingAdapter(); }
        @Override public Adapter caseTCPPlatform(TCPPlatform object) { return createTCPPlatformAdapter(); }
        @Override public Adapter defaultCase(EObject object)         { return createEObjectAdapter(); }
    };

    @Override
    public Adapter createAdapter(Notifier target) {
        return modelSwitch.doSwitch((EObject) target);
    }

    public Adapter createTCPBindingAdapter()  { return null; }
    public Adapter createTCPPlatformAdapter() { return null; }
    public Adapter createEObjectAdapter()     { return null; }

} // EdttcpAdapterFactory
