/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package edtdds;

import org.eclipse.emf.ecore.EObject;

/**
 * DDS transport binding configuration for a cross-platform link.
 * Exported as {name}.dds-binding.xml.
 * Schema: http://www.ecoa.technology/ddsbinding
 */
public interface DDSBinding extends EObject {

    String getName();
    void setName(String value);

    int getDomainId();
    void setDomainId(int value);

    String getTopicName();
    void setTopicName(String value);

} // DDSBinding
