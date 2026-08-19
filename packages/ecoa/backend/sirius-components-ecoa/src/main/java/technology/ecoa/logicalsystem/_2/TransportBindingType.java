/**
 * Copyright (c) 2023 Dassault Aviation
 *
 * SPDX-License-Identifier: MIT
 *
 */

package technology.ecoa.logicalsystem._2;

import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Transport Binding Type</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link technology.ecoa.logicalsystem._2.TransportBindingType#getParameters <em>Parameters</em>}</li>
 *   <li>{@link technology.ecoa.logicalsystem._2.TransportBindingType#getProtocol <em>Protocol</em>}</li>
 *   <li>{@link technology.ecoa.logicalsystem._2.TransportBindingType#isDds <em>Dds</em>}</li>
 *   <li>{@link technology.ecoa.logicalsystem._2.TransportBindingType#getDdsDomainId <em>Dds Domain Id</em>}</li>
 * </ul>
 *
 * @see technology.ecoa.logicalsystem._2.logPackage#getTransportBindingType()
 * @model extendedMetaData="name='transportBinding_._type' kind='empty'"
 * @generated
 */
public interface TransportBindingType extends EObject {
	/**
	 * Returns the value of the '<em><b>Parameters</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Parameters</em>' attribute.
	 * @see #setParameters(String)
	 * @see technology.ecoa.logicalsystem._2.logPackage#getTransportBindingType_Parameters()
	 * @model dataType="org.eclipse.emf.ecore.xml.type.AnyURI" required="true"
	 *        extendedMetaData="kind='attribute' name='parameters'"
	 * @generated
	 */
	String getParameters();

	/**
	 * Sets the value of the '{@link technology.ecoa.logicalsystem._2.TransportBindingType#getParameters <em>Parameters</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Parameters</em>' attribute.
	 * @see #getParameters()
	 * @generated
	 */
	void setParameters(String value);

	/**
	 * Returns the value of the '<em><b>Protocol</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Protocol</em>' attribute.
	 * @see #setProtocol(String)
	 * @see technology.ecoa.logicalsystem._2.logPackage#getTransportBindingType_Protocol()
	 * @model dataType="org.eclipse.emf.ecore.xml.type.String" required="true"
	 *        extendedMetaData="kind='attribute' name='protocol'"
	 * @generated
	 */
	String getProtocol();

	/**
	 * Sets the value of the '{@link technology.ecoa.logicalsystem._2.TransportBindingType#getProtocol <em>Protocol</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Protocol</em>' attribute.
	 * @see #getProtocol()
	 * @generated
	 */
	void setProtocol(String value);

	/**
	 * Returns the value of the '<em><b>Dds</b></em>' attribute.
	 * The default value is <code>"false"</code>.
	 * @return the value of the '<em>Dds</em>' attribute.
	 * @see #setDds(boolean)
	 * @see technology.ecoa.logicalsystem._2.logPackage#getTransportBindingType_Dds()
	 * @model default="false" dataType="org.eclipse.emf.ecore.xml.type.Boolean"
	 *        extendedMetaData="kind='attribute' name='dds'"
	 * @generated
	 */
	boolean isDds();

	/**
	 * Sets the value of the '{@link technology.ecoa.logicalsystem._2.TransportBindingType#isDds <em>Dds</em>}' attribute.
	 * @param value the new value of the '<em>Dds</em>' attribute.
	 * @see #isDds()
	 * @generated
	 */
	void setDds(boolean value);

	/**
	 * Returns the value of the '<em><b>Dds Domain Id</b></em>' attribute.
	 * The default value is <code>"0"</code>.
	 * @return the value of the '<em>Dds Domain Id</em>' attribute.
	 * @see #setDdsDomainId(long)
	 * @see technology.ecoa.logicalsystem._2.logPackage#getTransportBindingType_DdsDomainId()
	 * @model default="0" dataType="org.eclipse.emf.ecore.xml.type.UnsignedInt"
	 *        extendedMetaData="kind='attribute' name='ddsDomainId'"
	 * @generated
	 */
	long getDdsDomainId();

	/**
	 * Sets the value of the '{@link technology.ecoa.logicalsystem._2.TransportBindingType#getDdsDomainId <em>Dds Domain Id</em>}' attribute.
	 * @param value the new value of the '<em>Dds Domain Id</em>' attribute.
	 * @see #getDdsDomainId()
	 * @generated
	 */
	void setDdsDomainId(long value);

} // TransportBindingType
