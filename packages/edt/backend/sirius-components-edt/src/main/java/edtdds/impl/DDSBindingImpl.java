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
import edtdds.EdtddsPackage;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.impl.ENotificationImpl;
import org.eclipse.emf.ecore.impl.MinimalEObjectImpl;

public class DDSBindingImpl extends MinimalEObjectImpl.Container implements DDSBinding {

    protected static final String NAME_EDEFAULT       = null;
    protected static final int    DOMAIN_ID_EDEFAULT  = 0;
    protected static final String TOPIC_NAME_EDEFAULT = null;

    protected String name      = NAME_EDEFAULT;
    protected int    domainId  = DOMAIN_ID_EDEFAULT;
    protected String topicName = TOPIC_NAME_EDEFAULT;

    protected DDSBindingImpl() { super(); }

    @Override
    protected EClass eStaticClass() { return EdtddsPackage.Literals.DDS_BINDING; }

    @Override public String getName() { return name; }
    @Override public void setName(String newName) {
        String old = name; name = newName;
        if (eNotificationRequired())
            eNotify(new ENotificationImpl(this, Notification.SET, EdtddsPackage.DDS_BINDING__NAME, old, name));
    }

    @Override public int getDomainId() { return domainId; }
    @Override public void setDomainId(int newId) {
        int old = domainId; domainId = newId;
        if (eNotificationRequired())
            eNotify(new ENotificationImpl(this, Notification.SET, EdtddsPackage.DDS_BINDING__DOMAIN_ID, old, domainId));
    }

    @Override public String getTopicName() { return topicName; }
    @Override public void setTopicName(String newName) {
        String old = topicName; topicName = newName;
        if (eNotificationRequired())
            eNotify(new ENotificationImpl(this, Notification.SET, EdtddsPackage.DDS_BINDING__TOPIC_NAME, old, topicName));
    }

    @Override
    public Object eGet(int featureID, boolean resolve, boolean coreType) {
        switch (featureID) {
            case EdtddsPackage.DDS_BINDING__NAME:       return getName();
            case EdtddsPackage.DDS_BINDING__DOMAIN_ID:  return getDomainId();
            case EdtddsPackage.DDS_BINDING__TOPIC_NAME: return getTopicName();
        }
        return super.eGet(featureID, resolve, coreType);
    }

    @Override
    public void eSet(int featureID, Object newValue) {
        switch (featureID) {
            case EdtddsPackage.DDS_BINDING__NAME:       setName((String)  newValue); return;
            case EdtddsPackage.DDS_BINDING__DOMAIN_ID:  setDomainId((Integer) newValue); return;
            case EdtddsPackage.DDS_BINDING__TOPIC_NAME: setTopicName((String) newValue); return;
        }
        super.eSet(featureID, newValue);
    }

    @Override
    public void eUnset(int featureID) {
        switch (featureID) {
            case EdtddsPackage.DDS_BINDING__NAME:       setName(NAME_EDEFAULT); return;
            case EdtddsPackage.DDS_BINDING__DOMAIN_ID:  setDomainId(DOMAIN_ID_EDEFAULT); return;
            case EdtddsPackage.DDS_BINDING__TOPIC_NAME: setTopicName(TOPIC_NAME_EDEFAULT); return;
        }
        super.eUnset(featureID);
    }

    @Override
    public boolean eIsSet(int featureID) {
        switch (featureID) {
            case EdtddsPackage.DDS_BINDING__NAME:
                return NAME_EDEFAULT == null ? name != null : !NAME_EDEFAULT.equals(name);
            case EdtddsPackage.DDS_BINDING__DOMAIN_ID:
                return domainId != DOMAIN_ID_EDEFAULT;
            case EdtddsPackage.DDS_BINDING__TOPIC_NAME:
                return TOPIC_NAME_EDEFAULT == null ? topicName != null : !TOPIC_NAME_EDEFAULT.equals(topicName);
        }
        return super.eIsSet(featureID);
    }

    @Override
    public String toString() {
        if (eIsProxy()) return super.toString();
        return super.toString() + " (name: " + name + ", domainId: " + domainId + ", topicName: " + topicName + ')';
    }

} // DDSBindingImpl
