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

import org.eclipse.emf.ecore.EFactory;

/**
 * <!-- begin-user-doc --> The <b>Factory</b> for the model. It provides a
 * create method for each non-abstract class of the model. <!-- end-user-doc -->
 *
 * @see technology.ecoa.bin.desc._2.binPackage
 * @generated
 */
public interface binFactory extends EFactory {
	/**
	 * The singleton instance of the factory. <!-- begin-user-doc --> <!--
	 * end-user-doc -->
	 *
	 * @generated
	 */
	binFactory eINSTANCE = technology.ecoa.bin.desc._2.impl.binFactoryImpl.init();

	/**
	 * Returns a new object of class '<em>Binary Dependency</em>'. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 *
	 * @return a new object of class '<em>Binary Dependency</em>'.
	 * @generated
	 */
	BinaryDependency createBinaryDependency();

	/**
	 * Returns a new object of class '<em>Binary Module</em>'. <!-- begin-user-doc
	 * --> <!-- end-user-doc -->
	 *
	 * @return a new object of class '<em>Binary Module</em>'.
	 * @generated
	 */
	BinaryModule createBinaryModule();

	/**
	 * Returns a new object of class '<em>Bin Desc</em>'. <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 *
	 * @return a new object of class '<em>Bin Desc</em>'.
	 * @generated
	 */
	BinDesc createBinDesc();

	/**
	 * Returns a new object of class '<em>Document Root</em>'. <!-- begin-user-doc
	 * --> <!-- end-user-doc -->
	 *
	 * @return a new object of class '<em>Document Root</em>'.
	 * @generated
	 */
	DocumentRoot createDocumentRoot();

	/**
	 * Returns a new object of class '<em>Processor Target</em>'. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 *
	 * @return a new object of class '<em>Processor Target</em>'.
	 * @generated
	 */
	ProcessorTarget createProcessorTarget();

	/**
	 * Returns a new object of class '<em>Use Type</em>'. <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 *
	 * @return a new object of class '<em>Use Type</em>'.
	 * @generated
	 */
	UseType createUseType();

	/**
	 * Returns the package supported by this factory. <!-- begin-user-doc --> <!--
	 * end-user-doc -->
	 *
	 * @return the package supported by this factory.
	 * @generated
	 */
	binPackage getbinPackage();

} // binFactory
