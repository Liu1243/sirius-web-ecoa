/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package edttcp;

import org.eclipse.emf.ecore.EFactory;

/**
 * The <b>Factory</b> for the edttcp model.
 */
public interface EdttcpFactory extends EFactory {

    EdttcpFactory eINSTANCE = edttcp.impl.EdttcpFactoryImpl.init();

    TCPBinding createTCPBinding();

    TCPPlatform createTCPPlatform();

    EdttcpPackage getEdttcpPackage();

} // EdttcpFactory
