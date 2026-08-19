/*******************************************************************************
 * Copyright (c) 2021, 2023 Obeo.
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
package org.eclipse.sirius.components.representations;

import java.util.List;
import java.util.Objects;

/**
 * The message when the representation description handler fails.
 *
 * @author gcoutable
 */
public class Failure implements IStatus {

    private final List<Message> messages;

    public Failure(String message) {
        this.messages = List.of(new Message(Objects.requireNonNull(message), MessageLevel.ERROR));
    }

    public Failure(List<Message> messages) {
        this.messages = Objects.requireNonNull(messages);
    }

    public List<Message> getMessages() {
        return this.messages;
    }
}
