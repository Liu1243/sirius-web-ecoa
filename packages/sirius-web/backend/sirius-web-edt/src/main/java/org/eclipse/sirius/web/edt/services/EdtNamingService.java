/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.sirius.web.edt.services;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;
import org.open.oasis.docs.ns.opencsa.sca.sca.Component;
import org.open.oasis.docs.ns.opencsa.sca.sca.Property;
import org.springframework.stereotype.Service;

import edtbin.BinDesc;
import edtimplementation.ComponentImplementation;
import edtproject.ComponentDefinition;
import edtproject.Composite;
import edtproject.ServiceLink;
import edtqos.ServiceInstanceQos;


/**
 * Service for generating unique names for EDT model elements.
 * Replaces EcoaDtServicesDefaultName from Sirius Desktop.
 *
 * @author EDT Team
 */
@Service
public class EdtNamingService {

    /**
     * Generate a unique name for a new Component in a Composite.
     *
     * @param composite the Composite containing the Components
     * @return a unique component name
     */
    public String generateComponentName(Composite composite) {
        int size = composite.getComponents().size();
        return this.checkNameUnique("Component" + size,
                composite.getComponents().toArray(new Component[0]), size);
    }

    /**
     * Generate a unique name for a new Component based on an existing Component.
     *
     * @param component an existing Component in the Composite
     * @return a unique component name
     */
    public String generateComponentName(Component component) {
        Composite composite = (Composite) component.eContainer();
        return this.generateComponentName(composite);
    }

    /**
     * Generate a unique name for a new Property in a ComponentDefinition or Composite.
     *
     * @param property the Property being created
     * @return a unique property name
     */
    public String generatePropertyName(Property property) {
        String result = "";
        EObject container = property.eContainer();
        if (container instanceof ComponentDefinition cptDef) {
            int size = cptDef.getProperties().size();
            result = this.checkNameUnique("Property" + size,
                    cptDef.getProperties().toArray(new Property[0]), size);
        } else if (container instanceof Composite composite) {
            int size = composite.getProperties().size();
            result = this.checkNameUnique("Property" + size,
                    composite.getProperties().toArray(new Property[0]), size);
        }
        return result;
    }

    /**
     * Generate a unique name for a ServiceInstanceQos.
     *
     * @param serviceInstanceQos the ServiceInstanceQos being created
     * @return a unique QoS name
     */
    public String generateServiceQosName(ServiceInstanceQos serviceInstanceQos) {
        String result = "";
        EObject container = serviceInstanceQos.eContainer();
        if (container instanceof ComponentDefinition componentDefinition) {
            int size = componentDefinition.getAssociatedServiceQos().size();
            result = this.checkNameUnique("S" + size,
                    componentDefinition.getAssociatedServiceQos().toArray(new ServiceInstanceQos[0]), size);
        } else if (container instanceof ComponentImplementation componentImplementation) {
            int size = componentImplementation.getAssociatedServiceQos().size();
            result = this.checkNameUnique("S" + size,
                    componentImplementation.getAssociatedServiceQos().toArray(new ServiceInstanceQos[0]), size);
        }
        return result;
    }

    /**
     * Generate a unique name for a new ModuleType in a ComponentImplementation.
     * Uses the convention "Mt_N" where N is the count of existing module types.
     *
     * @param componentImplementation the container ComponentImplementation
     * @return a unique module type name
     */
    public String generateModuleTypeName(ComponentImplementation componentImplementation) {
        int size = componentImplementation.getModuleTypes().size();
        return this.checkNameUnique("Mt_" + size,
                componentImplementation.getModuleTypes().toArray(new EObject[0]), size);
    }

    /**
     * Check if the proposed name is unique, incrementing index if necessary.
     *
     * @param name the proposed name
     * @param objects existing objects to check against
     * @param index current index for name generation
     * @return a unique name
     */
    public String checkNameUnique(String name, EObject[] objects, int index) {
        for (EObject object : objects) {
            String objectName = this.getObjectName(object);
            if (Objects.equals(name, objectName)) {
                return this.checkNameUnique(name.replaceAll("" + index, "" + (index + 1)), objects, index + 1);
            }
        }
        return name;
    }

    /**
     * Get the name of an EObject using reflection.
     *
     * @param object the EObject
     * @return the name, or empty string if not found
     */
    public String getObjectName(EObject object) {
        String name = "";
        if (object instanceof ServiceLink wire) {
            name = " the ServiceLink between " + wire.getSource().getWireString() + " and "
                    + wire.getTarget().getWireString();
        } else if (object instanceof BinDesc binDesc) {
            name = binDesc.getFileName();
        } else if (object != null) {
            name = getNameViaReflection(object);
        }
        return name != null ? name : "";
    }

    private String getNameViaReflection(EObject object) {
        String result = this.tryGetNameMethod(object);
        if (result == null || result.isEmpty()) {
            String idResult = this.tryGetIdMethod(object);
            if (idResult != null) {
                result = idResult;
            }
        }
        return result != null ? result : "";
    }

    private String tryGetNameMethod(EObject object) {
        try {
            Method declaredMethod = object.getClass().getMethod("getName");
            return (String) declaredMethod.invoke(object);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return "";
        }
    }

    private String tryGetIdMethod(EObject object) {
        try {
            Method declaredMethod = object.getClass().getMethod("getId");
            return (String) declaredMethod.invoke(object);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return "";
        }
    }
}
