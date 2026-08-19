/*******************************************************************************
 * Copyright (c) 2019, 2025 Obeo.
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
package org.eclipse.sirius.components.interpreter;

import java.util.Objects;
import java.util.function.Function;

import org.eclipse.sirius.components.representations.VariableManager;

/**
 * Utility class used to provide a string value from value expression.
 *
 * @author sbegaudeau
 */
public class StringValueProvider implements Function<VariableManager, String> {

    private final AQLInterpreter interpreter;

    private final String expression;

    public StringValueProvider(AQLInterpreter interpreter, String expression) {
        this.interpreter = Objects.requireNonNull(interpreter);
        this.expression = expression;
    }

    @Override
    public String apply(VariableManager variableManager) {
        Result result = this.interpreter.evaluateExpression(variableManager.getVariables(), this.expression);
        return result.asString()
                .orElse("");
    }
}
