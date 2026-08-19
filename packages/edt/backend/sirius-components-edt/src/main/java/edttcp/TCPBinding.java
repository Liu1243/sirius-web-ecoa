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

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;

/**
 * A representation of the model object '<em><b>TCP Binding</b></em>'.
 * Holds TCP transport configuration for a cross-platform link.
 * Exported as {name}.tcp-params.xml.
 */
public interface TCPBinding extends EObject {

    String getName();
    void setName(String value);

    EList<TCPPlatform> getPlatform();

} // TCPBinding
