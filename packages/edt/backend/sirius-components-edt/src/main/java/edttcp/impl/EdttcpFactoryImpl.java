/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package edttcp.impl;

import edttcp.EdttcpFactory;
import edttcp.EdttcpPackage;
import edttcp.TCPBinding;
import edttcp.TCPPlatform;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.impl.EFactoryImpl;
import org.eclipse.emf.ecore.plugin.EcorePlugin;

public class EdttcpFactoryImpl extends EFactoryImpl implements EdttcpFactory {

    public static EdttcpFactory init() {
        try {
            EdttcpFactory factory = (EdttcpFactory) EPackage.Registry.INSTANCE.getEFactory(EdttcpPackage.eNS_URI);
            if (factory != null) return factory;
        } catch (Exception e) {
            EcorePlugin.INSTANCE.log(e);
        }
        return new EdttcpFactoryImpl();
    }

    public EdttcpFactoryImpl() {
        super();
    }

    @Override
    public EObject create(EClass eClass) {
        switch (eClass.getClassifierID()) {
            case EdttcpPackage.TCP_BINDING:  return createTCPBinding();
            case EdttcpPackage.TCP_PLATFORM: return createTCPPlatform();
            default: throw new IllegalArgumentException("The class '" + eClass.getName() + "' is not a valid classifier");
        }
    }

    @Override
    public TCPBinding createTCPBinding() {
        return new TCPBindingImpl();
    }

    @Override
    public TCPPlatform createTCPPlatform() {
        return new TCPPlatformImpl();
    }

    @Override
    public EdttcpPackage getEdttcpPackage() {
        return (EdttcpPackage) getEPackage();
    }

} // EdttcpFactoryImpl
