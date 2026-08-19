/**
 * Copyright (c) 2024, 2025 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 */
package technology.ecoa.bin.desc._2;

import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc --> A representation of the model object '<em><b>Use
 * Type</b></em>'. <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 * <li>{@link technology.ecoa.bin.desc._2.UseType#getLibrary
 * <em>Library</em>}</li>
 * </ul>
 *
 * @see technology.ecoa.bin.desc._2.binPackage#getUseType()
 * @model extendedMetaData="name='use_._type' kind='empty'"
 * @generated
 */
public interface UseType extends EObject {
	/**
	 * Returns the value of the '<em><b>Library</b></em>' attribute. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 *
	 * @return the value of the '<em>Library</em>' attribute.
	 * @see #setLibrary(String)
	 * @see technology.ecoa.bin.desc._2.binPackage#getUseType_Library()
	 * @model dataType="technology.ecoa.bin.desc._2.LibraryName" required="true"
	 *        extendedMetaData="kind='attribute' name='library'"
	 * @generated
	 */
	String getLibrary();

	/**
	 * Sets the value of the '{@link technology.ecoa.bin.desc._2.UseType#getLibrary
	 * <em>Library</em>}' attribute. <!-- begin-user-doc --> <!-- end-user-doc -->
	 *
	 * @param value the new value of the '<em>Library</em>' attribute.
	 * @see #getLibrary()
	 * @generated
	 */
	void setLibrary(String value);

} // UseType
