/*******************************************************************************
 * Copyright (c) 2025 Obeo.
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
package org.eclipse.sirius.web.edt.services;

import org.eclipse.sirius.components.view.ColorPalette;
import org.eclipse.sirius.components.view.FixedColor;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;

/**
 * Used to provide the color palette.
 *
 * @author managerial
 */
public class EdtColorPaletteProvider {

    public static final String EDGE_COLOR = "edge.color";

    public static final String COMPONENT_TEXT = "component.text";

    public static final String COMPONENT_BACKGROUND = "component.background";

    public static final String COMPONENT_BORDER = "component.border";

    public static final String SERVICE_TEXT = "service.text";

    public static final String SERVICE_BACKGROUND = "service.background";

    public static final String SERVICE_BORDER = "service.border";

    public static final String REFERENCE_TEXT = "reference.text";

    public static final String REFERENCE_BACKGROUND = "reference.background";

    public static final String REFERENCE_BORDER = "reference.border";

    public static final String PROPERTY_TEXT = "property.text";

    public static final String PROPERTY_BACKGROUND = "property.background";

    public static final String PROPERTY_BORDER = "property.border";

    // Logical System Diagram colors
    public static final String PLATFORM_TEXT = "platform.text";

    public static final String PLATFORM_BACKGROUND = "platform.background";

    public static final String PLATFORM_BORDER = "platform.border";

    public static final String LOGICAL_NODE_TEXT = "logicalnode.text";

    public static final String LOGICAL_NODE_BACKGROUND = "logicalnode.background";

    public static final String LOGICAL_NODE_BORDER = "logicalnode.border";

    public static final String PROTECTION_DOMAIN_TEXT = "protectiondomain.text";

    public static final String PROTECTION_DOMAIN_BACKGROUND = "protectiondomain.background";

    public static final String PROTECTION_DOMAIN_BORDER = "protectiondomain.border";

    public static final String DEPLOYED_INSTANCE_TEXT = "deployedinstance.text";

    public static final String DEPLOYED_INSTANCE_BACKGROUND = "deployedinstance.background";

    public static final String DEPLOYED_INSTANCE_BORDER = "deployedinstance.border";

    public static final String PLATFORM_LINK_COLOR = "platformlink.color";

    public static final String NODE_LINK_COLOR = "nodelink.color";

    // Component Implementation Diagram colors
    public static final String EXTERNAL_TEXT = "external.text";

    public static final String EXTERNAL_BACKGROUND = "external.background";

    public static final String EXTERNAL_BORDER = "external.border";

    public static final String MODULE_INSTANCE_TEXT = "moduleinstance.text";

    public static final String MODULE_INSTANCE_BACKGROUND = "moduleinstance.background";

    public static final String MODULE_INSTANCE_BORDER = "moduleinstance.border";

    public static final String TRIGGER_INSTANCE_TEXT = "triggerinstance.text";

    public static final String TRIGGER_INSTANCE_BACKGROUND = "triggerinstance.background";

    public static final String TRIGGER_INSTANCE_BORDER = "triggerinstance.border";

    public static final String DYNAMIC_TRIGGER_INSTANCE_TEXT = "dynamictriggerinstance.text";

    public static final String DYNAMIC_TRIGGER_INSTANCE_BACKGROUND = "dynamictriggerinstance.background";

    public static final String DYNAMIC_TRIGGER_INSTANCE_BORDER = "dynamictriggerinstance.border";

    // Operation port colors
    public static final String WRITER_PORT_BACKGROUND = "writerport.background";

    public static final String READER_PORT_BACKGROUND = "readerport.background";

    public static final String CLIENT_PORT_BACKGROUND = "clientport.background";

    public static final String SERVER_PORT_BACKGROUND = "serverport.background";

    public static final String SENDER_PORT_BACKGROUND = "senderport.background";

    public static final String RECEIVER_PORT_BACKGROUND = "receiverport.background";

    public static final String PORT_BORDER = "port.border";

    public static final String PORT_TEXT = "port.text";

    // Component Implementation link colors
    public static final String DATA_LINK_COLOR = "datalink.color";

    public static final String REQUEST_LINK_COLOR = "requestlink.color";

    public static final String EVENT_LINK_COLOR = "eventlink.color";

    private static final String BLACK = "#000000";

    private static final String WHITE = "#FFFFFF";

    public ColorPalette getColorPalette() {
        return new ViewBuilders().newColorPalette()
                .name("Edt Color Palette")
                .colors(
                        this.fixedColor(EDGE_COLOR, BLACK),
                        this.fixedColor(COMPONENT_TEXT, BLACK),
                        this.fixedColor(COMPONENT_BACKGROUND, "#E3F2FD"),
                        this.fixedColor(COMPONENT_BORDER, "#2196F3"),
                        this.fixedColor(SERVICE_TEXT, BLACK),
                        this.fixedColor(SERVICE_BACKGROUND, "#F3E5F5"),
                        this.fixedColor(SERVICE_BORDER, "#9C27B0"),
                        this.fixedColor(REFERENCE_TEXT, BLACK),
                        this.fixedColor(REFERENCE_BACKGROUND, "#E0F2F1"),
                        this.fixedColor(REFERENCE_BORDER, "#009688"),
                        this.fixedColor(PROPERTY_TEXT, BLACK),
                        this.fixedColor(PROPERTY_BACKGROUND, "#FFF3E0"),
                        this.fixedColor(PROPERTY_BORDER, "#FF9800"),
                        // Logical System Diagram colors
                        this.fixedColor(PLATFORM_TEXT, BLACK),
                        this.fixedColor(PLATFORM_BACKGROUND, WHITE),
                        this.fixedColor(PLATFORM_BORDER, BLACK),
                        this.fixedColor(LOGICAL_NODE_TEXT, BLACK),
                        this.fixedColor(LOGICAL_NODE_BACKGROUND, "#FFE0B2"),
                        this.fixedColor(LOGICAL_NODE_BORDER, "#FF9800"),
                        this.fixedColor(PROTECTION_DOMAIN_TEXT, BLACK),
                        this.fixedColor(PROTECTION_DOMAIN_BACKGROUND, "#BBDEFB"),
                        this.fixedColor(PROTECTION_DOMAIN_BORDER, "#2196F3"),
                        this.fixedColor(DEPLOYED_INSTANCE_TEXT, BLACK),
                        this.fixedColor(DEPLOYED_INSTANCE_BACKGROUND, "#C8E6C9"),
                        this.fixedColor(DEPLOYED_INSTANCE_BORDER, "#4CAF50"),
                        this.fixedColor(PLATFORM_LINK_COLOR, "#607D8B"),
                        this.fixedColor(NODE_LINK_COLOR, "#795548"),
                        // Component Implementation Diagram colors
                        this.fixedColor(EXTERNAL_TEXT, BLACK),
                        this.fixedColor(EXTERNAL_BACKGROUND, "#E0E0E0"),
                        this.fixedColor(EXTERNAL_BORDER, "#424242"),
                        this.fixedColor(MODULE_INSTANCE_TEXT, BLACK),
                        this.fixedColor(MODULE_INSTANCE_BACKGROUND, "#F5F5F5"),
                        this.fixedColor(MODULE_INSTANCE_BORDER, "#616161"),
                        this.fixedColor(TRIGGER_INSTANCE_TEXT, BLACK),
                        this.fixedColor(TRIGGER_INSTANCE_BACKGROUND, "#FFF8E1"),
                        this.fixedColor(TRIGGER_INSTANCE_BORDER, "#F9A825"),
                        this.fixedColor(DYNAMIC_TRIGGER_INSTANCE_TEXT, BLACK),
                        this.fixedColor(DYNAMIC_TRIGGER_INSTANCE_BACKGROUND, "#F3E5F5"),
                        this.fixedColor(DYNAMIC_TRIGGER_INSTANCE_BORDER, "#8E24AA"),
                        // Operation port colors (W=gray, R=gray, C=yellow, S=yellow, S=blue, R=blue)
                        this.fixedColor(WRITER_PORT_BACKGROUND, "#9E9E9E"),
                        this.fixedColor(READER_PORT_BACKGROUND, "#BDBDBD"),
                        this.fixedColor(CLIENT_PORT_BACKGROUND, "#FDD835"),
                        this.fixedColor(SERVER_PORT_BACKGROUND, "#FFEE58"),
                        this.fixedColor(SENDER_PORT_BACKGROUND, "#90CAF9"),
                        this.fixedColor(RECEIVER_PORT_BACKGROUND, "#BBDEFB"),
                        this.fixedColor(PORT_BORDER, "#757575"),
                        this.fixedColor(PORT_TEXT, BLACK),
                        // Link colors: DataLink=black, RequestLink=orange, EventLink=blue
                        this.fixedColor(DATA_LINK_COLOR, BLACK),
                        this.fixedColor(REQUEST_LINK_COLOR, "#E65100"),
                        this.fixedColor(EVENT_LINK_COLOR, "#1565C0")
                )
                .build();
    }

    private FixedColor fixedColor(String name, String value) {
        return new ViewBuilders().newFixedColor()
                .name(name)
                .value(value)
                .build();
    }
}
