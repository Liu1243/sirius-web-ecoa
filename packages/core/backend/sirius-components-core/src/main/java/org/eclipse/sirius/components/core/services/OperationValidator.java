/*******************************************************************************
 * Copyright (c) 2023, 2025 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/

package org.eclipse.sirius.components.core.services;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.sirius.components.core.api.variables.IVariableProvider;
import org.eclipse.sirius.components.representations.IOperationValidator;
import org.eclipse.sirius.components.representations.Variable;
import org.springframework.stereotype.Service;

/**
 * Used to validate the variables given for the execution of an operation.
 *
 * @author sbegaudeau
 */
@Service
public class OperationValidator implements IOperationValidator {

    private final List<IVariableProvider> variableProviders;

    public OperationValidator(List<IVariableProvider> variableProviders) {
        this.variableProviders = Objects.requireNonNull(variableProviders);
    }

    @Override
    public void validate(String operationName, Map<String, Object> variables) {
        var expectedVariables = this.variableProviders.stream()
                .map(variableProvider -> variableProvider.getVariables(operationName))
                .flatMap(List::stream)
                .toList();

        variables.entrySet().stream()
                .forEach(entry -> this.validate(operationName, expectedVariables, entry.getKey(), entry.getValue()));
    }

    private void validate(String operationName, List<Variable> expectedVariables, String variableName, Object value) {
        // validation logic removed (was only logging)
    }
}
