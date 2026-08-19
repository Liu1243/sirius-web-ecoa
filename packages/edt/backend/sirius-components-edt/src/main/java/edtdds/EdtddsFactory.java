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

import org.eclipse.emf.ecore.EFactory;

public interface EdtddsFactory extends EFactory {

    EdtddsFactory eINSTANCE = edtdds.impl.EdtddsFactoryImpl.init();

    DDSBinding createDDSBinding();

    EdtddsPackage getEdtddsPackage();

} // EdtddsFactory
