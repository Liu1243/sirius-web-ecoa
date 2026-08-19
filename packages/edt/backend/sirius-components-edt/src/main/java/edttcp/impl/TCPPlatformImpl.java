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

import edttcp.EdttcpPackage;
import edttcp.TCPPlatform;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.impl.ENotificationImpl;
import org.eclipse.emf.ecore.impl.MinimalEObjectImpl;

public class TCPPlatformImpl extends MinimalEObjectImpl.Container implements TCPPlatform {

    protected static final String NAME_EDEFAULT = null;
    protected static final String ADDRESS_EDEFAULT = null;
    protected static final int PORT_EDEFAULT = 0;

    protected String name = NAME_EDEFAULT;
    protected String address = ADDRESS_EDEFAULT;
    protected int port = PORT_EDEFAULT;

    protected TCPPlatformImpl() {
        super();
    }

    @Override
    protected EClass eStaticClass() {
        return EdttcpPackage.Literals.TCP_PLATFORM;
    }

    @Override
    public String getName() { return name; }

    @Override
    public void setName(String newName) {
        String old = name; name = newName;
        if (eNotificationRequired())
            eNotify(new ENotificationImpl(this, Notification.SET, EdttcpPackage.TCP_PLATFORM__NAME, old, name));
    }

    @Override
    public String getAddress() { return address; }

    @Override
    public void setAddress(String newAddress) {
        String old = address; address = newAddress;
        if (eNotificationRequired())
            eNotify(new ENotificationImpl(this, Notification.SET, EdttcpPackage.TCP_PLATFORM__ADDRESS, old, address));
    }

    @Override
    public int getPort() { return port; }

    @Override
    public void setPort(int newPort) {
        int old = port; port = newPort;
        if (eNotificationRequired())
            eNotify(new ENotificationImpl(this, Notification.SET, EdttcpPackage.TCP_PLATFORM__PORT, old, port));
    }

    @Override
    public Object eGet(int featureID, boolean resolve, boolean coreType) {
        switch (featureID) {
            case EdttcpPackage.TCP_PLATFORM__NAME:    return getName();
            case EdttcpPackage.TCP_PLATFORM__ADDRESS: return getAddress();
            case EdttcpPackage.TCP_PLATFORM__PORT:    return getPort();
        }
        return super.eGet(featureID, resolve, coreType);
    }

    @Override
    public void eSet(int featureID, Object newValue) {
        switch (featureID) {
            case EdttcpPackage.TCP_PLATFORM__NAME:    setName((String) newValue); return;
            case EdttcpPackage.TCP_PLATFORM__ADDRESS: setAddress((String) newValue); return;
            case EdttcpPackage.TCP_PLATFORM__PORT:    setPort((Integer) newValue); return;
        }
        super.eSet(featureID, newValue);
    }

    @Override
    public void eUnset(int featureID) {
        switch (featureID) {
            case EdttcpPackage.TCP_PLATFORM__NAME:    setName(NAME_EDEFAULT); return;
            case EdttcpPackage.TCP_PLATFORM__ADDRESS: setAddress(ADDRESS_EDEFAULT); return;
            case EdttcpPackage.TCP_PLATFORM__PORT:    setPort(PORT_EDEFAULT); return;
        }
        super.eUnset(featureID);
    }

    @Override
    public boolean eIsSet(int featureID) {
        switch (featureID) {
            case EdttcpPackage.TCP_PLATFORM__NAME:
                return NAME_EDEFAULT == null ? name != null : !NAME_EDEFAULT.equals(name);
            case EdttcpPackage.TCP_PLATFORM__ADDRESS:
                return ADDRESS_EDEFAULT == null ? address != null : !ADDRESS_EDEFAULT.equals(address);
            case EdttcpPackage.TCP_PLATFORM__PORT:
                return port != PORT_EDEFAULT;
        }
        return super.eIsSet(featureID);
    }

    @Override
    public String toString() {
        if (eIsProxy()) return super.toString();
        return super.toString() + " (name: " + name + ", address: " + address + ", port: " + port + ')';
    }

} // TCPPlatformImpl
