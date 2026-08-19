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
import edttcp.TCPBinding;
import edttcp.TCPPlatform;

import java.util.Collection;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.NotificationChain;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.impl.ENotificationImpl;
import org.eclipse.emf.ecore.impl.MinimalEObjectImpl;
import org.eclipse.emf.ecore.util.EObjectContainmentEList;
import org.eclipse.emf.ecore.util.InternalEList;

public class TCPBindingImpl extends MinimalEObjectImpl.Container implements TCPBinding {

    protected static final String NAME_EDEFAULT = null;
    protected String name = NAME_EDEFAULT;
    protected EList<TCPPlatform> platform;

    protected TCPBindingImpl() {
        super();
    }

    @Override
    protected EClass eStaticClass() {
        return EdttcpPackage.Literals.TCP_BINDING;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String newName) {
        String oldName = name;
        name = newName;
        if (eNotificationRequired())
            eNotify(new ENotificationImpl(this, Notification.SET, EdttcpPackage.TCP_BINDING__NAME, oldName, name));
    }

    @Override
    public EList<TCPPlatform> getPlatform() {
        if (platform == null) {
            platform = new EObjectContainmentEList<>(TCPPlatform.class, this, EdttcpPackage.TCP_BINDING__PLATFORM);
        }
        return platform;
    }

    @Override
    public NotificationChain eInverseRemove(InternalEObject otherEnd, int featureID, NotificationChain msgs) {
        switch (featureID) {
            case EdttcpPackage.TCP_BINDING__PLATFORM:
                return ((InternalEList<?>) getPlatform()).basicRemove(otherEnd, msgs);
        }
        return super.eInverseRemove(otherEnd, featureID, msgs);
    }

    @Override
    public Object eGet(int featureID, boolean resolve, boolean coreType) {
        switch (featureID) {
            case EdttcpPackage.TCP_BINDING__NAME:    return getName();
            case EdttcpPackage.TCP_BINDING__PLATFORM: return getPlatform();
        }
        return super.eGet(featureID, resolve, coreType);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void eSet(int featureID, Object newValue) {
        switch (featureID) {
            case EdttcpPackage.TCP_BINDING__NAME:
                setName((String) newValue); return;
            case EdttcpPackage.TCP_BINDING__PLATFORM:
                getPlatform().clear();
                getPlatform().addAll((Collection<? extends TCPPlatform>) newValue); return;
        }
        super.eSet(featureID, newValue);
    }

    @Override
    public void eUnset(int featureID) {
        switch (featureID) {
            case EdttcpPackage.TCP_BINDING__NAME:    setName(NAME_EDEFAULT); return;
            case EdttcpPackage.TCP_BINDING__PLATFORM: getPlatform().clear(); return;
        }
        super.eUnset(featureID);
    }

    @Override
    public boolean eIsSet(int featureID) {
        switch (featureID) {
            case EdttcpPackage.TCP_BINDING__NAME:
                return NAME_EDEFAULT == null ? name != null : !NAME_EDEFAULT.equals(name);
            case EdttcpPackage.TCP_BINDING__PLATFORM:
                return platform != null && !platform.isEmpty();
        }
        return super.eIsSet(featureID);
    }

    @Override
    public String toString() {
        if (eIsProxy()) return super.toString();
        return super.toString() + " (name: " + name + ')';
    }

} // TCPBindingImpl
