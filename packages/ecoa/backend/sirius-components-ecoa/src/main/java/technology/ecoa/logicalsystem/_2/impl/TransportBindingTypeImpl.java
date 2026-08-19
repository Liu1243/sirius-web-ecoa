/**
 * Copyright (c) 2023 Dassault Aviation
 *
 * SPDX-License-Identifier: MIT
 *
 */

package technology.ecoa.logicalsystem._2.impl;

import org.eclipse.emf.common.notify.Notification;

import org.eclipse.emf.ecore.EClass;

import org.eclipse.emf.ecore.impl.ENotificationImpl;
import org.eclipse.emf.ecore.impl.MinimalEObjectImpl;

import technology.ecoa.logicalsystem._2.TransportBindingType;
import technology.ecoa.logicalsystem._2.logPackage;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model object '<em><b>Transport Binding Type</b></em>'.
 * <!-- end-user-doc -->
 * <p>
 * The following features are implemented:
 * </p>
 * <ul>
 *   <li>{@link technology.ecoa.logicalsystem._2.impl.TransportBindingTypeImpl#getParameters <em>Parameters</em>}</li>
 *   <li>{@link technology.ecoa.logicalsystem._2.impl.TransportBindingTypeImpl#getProtocol <em>Protocol</em>}</li>
 *   <li>{@link technology.ecoa.logicalsystem._2.impl.TransportBindingTypeImpl#isDds <em>Dds</em>}</li>
 *   <li>{@link technology.ecoa.logicalsystem._2.impl.TransportBindingTypeImpl#getDdsDomainId <em>Dds Domain Id</em>}</li>
 * </ul>
 *
 * @generated
 */
public class TransportBindingTypeImpl extends MinimalEObjectImpl.Container implements TransportBindingType {
	/**
	 * The default value of the '{@link #getParameters() <em>Parameters</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getParameters()
	 * @generated
	 * @ordered
	 */
	protected static final String PARAMETERS_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getParameters() <em>Parameters</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getParameters()
	 * @generated
	 * @ordered
	 */
	protected String parameters = PARAMETERS_EDEFAULT;

	/**
	 * The default value of the '{@link #getProtocol() <em>Protocol</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getProtocol()
	 * @generated
	 * @ordered
	 */
	protected static final String PROTOCOL_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getProtocol() <em>Protocol</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getProtocol()
	 * @generated
	 * @ordered
	 */
	protected String protocol = PROTOCOL_EDEFAULT;

	/**
	 * The default value of the '{@link #isDds() <em>Dds</em>}' attribute.
	 * @see #isDds()
	 * @generated
	 * @ordered
	 */
	protected static final boolean DDS_EDEFAULT = false;

	/**
	 * The cached value of the '{@link #isDds() <em>Dds</em>}' attribute.
	 * @see #isDds()
	 * @generated
	 * @ordered
	 */
	protected boolean dds = DDS_EDEFAULT;

	/**
	 * The default value of the '{@link #getDdsDomainId() <em>Dds Domain Id</em>}' attribute.
	 * @see #getDdsDomainId()
	 * @generated
	 * @ordered
	 */
	protected static final long DDS_DOMAIN_ID_EDEFAULT = 0L;

	/**
	 * The cached value of the '{@link #getDdsDomainId() <em>Dds Domain Id</em>}' attribute.
	 * @see #getDdsDomainId()
	 * @generated
	 * @ordered
	 */
	protected long ddsDomainId = DDS_DOMAIN_ID_EDEFAULT;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	protected TransportBindingTypeImpl() {
		super();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	protected EClass eStaticClass() {
		return logPackage.Literals.TRANSPORT_BINDING_TYPE;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public String getParameters() {
		return parameters;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public void setParameters(String newParameters) {
		String oldParameters = parameters;
		parameters = newParameters;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, logPackage.TRANSPORT_BINDING_TYPE__PARAMETERS, oldParameters, parameters));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public String getProtocol() {
		return protocol;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public void setProtocol(String newProtocol) {
		String oldProtocol = protocol;
		protocol = newProtocol;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, logPackage.TRANSPORT_BINDING_TYPE__PROTOCOL, oldProtocol, protocol));
	}

	/**
	 * @generated
	 */
	public boolean isDds() {
		return dds;
	}

	/**
	 * @generated
	 */
	public void setDds(boolean newDds) {
		boolean oldDds = dds;
		dds = newDds;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, logPackage.TRANSPORT_BINDING_TYPE__DDS, oldDds, dds));
	}

	/**
	 * @generated
	 */
	public long getDdsDomainId() {
		return ddsDomainId;
	}

	/**
	 * @generated
	 */
	public void setDdsDomainId(long newDdsDomainId) {
		long oldDdsDomainId = ddsDomainId;
		ddsDomainId = newDdsDomainId;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, logPackage.TRANSPORT_BINDING_TYPE__DDS_DOMAIN_ID, oldDdsDomainId, ddsDomainId));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Object eGet(int featureID, boolean resolve, boolean coreType) {
		switch (featureID) {
			case logPackage.TRANSPORT_BINDING_TYPE__PARAMETERS:
				return getParameters();
			case logPackage.TRANSPORT_BINDING_TYPE__PROTOCOL:
				return getProtocol();
			case logPackage.TRANSPORT_BINDING_TYPE__DDS:
				return isDds();
			case logPackage.TRANSPORT_BINDING_TYPE__DDS_DOMAIN_ID:
				return getDdsDomainId();
		}
		return super.eGet(featureID, resolve, coreType);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void eSet(int featureID, Object newValue) {
		switch (featureID) {
			case logPackage.TRANSPORT_BINDING_TYPE__PARAMETERS:
				setParameters((String)newValue);
				return;
			case logPackage.TRANSPORT_BINDING_TYPE__PROTOCOL:
				setProtocol((String)newValue);
				return;
			case logPackage.TRANSPORT_BINDING_TYPE__DDS:
				setDds((Boolean)newValue);
				return;
			case logPackage.TRANSPORT_BINDING_TYPE__DDS_DOMAIN_ID:
				setDdsDomainId((Long)newValue);
				return;
		}
		super.eSet(featureID, newValue);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void eUnset(int featureID) {
		switch (featureID) {
			case logPackage.TRANSPORT_BINDING_TYPE__PARAMETERS:
				setParameters(PARAMETERS_EDEFAULT);
				return;
			case logPackage.TRANSPORT_BINDING_TYPE__PROTOCOL:
				setProtocol(PROTOCOL_EDEFAULT);
				return;
			case logPackage.TRANSPORT_BINDING_TYPE__DDS:
				setDds(DDS_EDEFAULT);
				return;
			case logPackage.TRANSPORT_BINDING_TYPE__DDS_DOMAIN_ID:
				setDdsDomainId(DDS_DOMAIN_ID_EDEFAULT);
				return;
		}
		super.eUnset(featureID);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public boolean eIsSet(int featureID) {
		switch (featureID) {
			case logPackage.TRANSPORT_BINDING_TYPE__PARAMETERS:
				return PARAMETERS_EDEFAULT == null ? parameters != null : !PARAMETERS_EDEFAULT.equals(parameters);
			case logPackage.TRANSPORT_BINDING_TYPE__PROTOCOL:
				return PROTOCOL_EDEFAULT == null ? protocol != null : !PROTOCOL_EDEFAULT.equals(protocol);
			case logPackage.TRANSPORT_BINDING_TYPE__DDS:
				return dds != DDS_EDEFAULT;
			case logPackage.TRANSPORT_BINDING_TYPE__DDS_DOMAIN_ID:
				return ddsDomainId != DDS_DOMAIN_ID_EDEFAULT;
		}
		return super.eIsSet(featureID);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String toString() {
		if (eIsProxy()) return super.toString();

		StringBuilder result = new StringBuilder(super.toString());
		result.append(" (parameters: ");
		result.append(parameters);
		result.append(", protocol: ");
		result.append(protocol);
		result.append(", dds: ");
		result.append(dds);
		result.append(", ddsDomainId: ");
		result.append(ddsDomainId);
		result.append(')');
		return result.toString();
	}

} //TransportBindingTypeImpl
