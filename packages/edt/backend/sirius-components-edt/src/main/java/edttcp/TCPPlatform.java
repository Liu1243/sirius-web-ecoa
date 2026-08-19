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

import org.eclipse.emf.ecore.EObject;

/**
 * A representation of the model object '<em><b>TCP Platform</b></em>'.
 * Defines the TCP endpoint (IP address + port) for one ECOA computing platform.
 */
public interface TCPPlatform extends EObject {

    String getName();
    void setName(String value);

    String getAddress();
    void setAddress(String value);

    int getPort();
    void setPort(int value);

} // TCPPlatform
