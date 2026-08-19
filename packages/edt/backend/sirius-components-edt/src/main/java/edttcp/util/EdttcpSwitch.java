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

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.util.Switch;

public class EdttcpSwitch<T> extends Switch<T> {

    protected static EdttcpPackage modelPackage;

    public EdttcpSwitch() {
        if (modelPackage == null) {
            modelPackage = EdttcpPackage.eINSTANCE;
        }
    }

    @Override
    protected boolean isSwitchFor(EPackage ePackage) {
        return ePackage == modelPackage;
    }

    @Override
    protected T doSwitch(int classifierID, EObject theEObject) {
        switch (classifierID) {
            case EdttcpPackage.TCP_BINDING: {
                TCPBinding obj = (TCPBinding) theEObject;
                T result = caseTCPBinding(obj);
                if (result == null) result = defaultCase(theEObject);
                return result;
            }
            case EdttcpPackage.TCP_PLATFORM: {
                TCPPlatform obj = (TCPPlatform) theEObject;
                T result = caseTCPPlatform(obj);
                if (result == null) result = defaultCase(theEObject);
                return result;
            }
            default: return defaultCase(theEObject);
        }
    }

    public T caseTCPBinding(TCPBinding object)   { return null; }
    public T caseTCPPlatform(TCPPlatform object) { return null; }

    @Override
    public T defaultCase(EObject object) { return null; }

} // EdttcpSwitch
