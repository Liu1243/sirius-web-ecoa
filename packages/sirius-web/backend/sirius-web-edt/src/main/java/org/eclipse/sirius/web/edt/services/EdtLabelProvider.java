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

import edtbin.provider.EdtbinItemProviderAdapterFactory;
import edtdeployment.provider.EdtdeploymentItemProviderAdapterFactory;
import edtimplementation.provider.EdtimplementationItemProviderAdapterFactory;
import edtinterface.EDTInterfacePackage;
import edtinterface.provider.EDTInterfaceItemProviderAdapterFactory;
import edtlogical.provider.EdtlogicalItemProviderAdapterFactory;
import edtproject.*;
import edtproject.provider.EDTProjectItemProviderAdapterFactory;
import edtqos.provider.EdtqosItemProviderAdapterFactory;
import edttype.provider.EDTTypeItemProviderAdapterFactory;
import edtudp.provider.EdtudpItemProviderAdapterFactory;
import edtuid.provider.EdtuidItemProviderAdapterFactory;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.edit.provider.IItemLabelProvider;
import org.eclipse.emf.edit.provider.IItemStyledLabelProvider;
import org.eclipse.sirius.components.core.api.labels.StyledString;
import org.eclipse.sirius.components.emf.services.api.IDefaultEMFLabelService;
import org.eclipse.sirius.components.emf.services.api.IEMFLabelServiceDelegate;
import org.eclipse.sirius.components.emf.services.api.IStyledStringConverter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Used to customize the default label for edt objects.
 *
 * @author managerial
 */
@Service
public class EdtLabelProvider implements IEMFLabelServiceDelegate {

    // List of known EDT package NS URIs or prefixes could be used.
    // For now, we rely on checking if the object's package is one of the known ones.
    private static final Set<String> EDT_NS_URIS = Set.of(
            EDTProjectPackage.eNS_URI,
            EDTInterfacePackage.eNS_URI,
            "EDTType", // Hypothetical, need to check if compilation fails
            "EDTDeployment",
            "EDTImplementation",
            "EDTLogicalSystem",
            "EDTQoS",
            "EDTUID",
            "EDTBin"
    );

    private final IStyledStringConverter styledStringConverter;

    private final IDefaultEMFLabelService defaultEMFLabelService;



    public EdtLabelProvider(IStyledStringConverter styledStringConverter, IDefaultEMFLabelService defaultEMFLabelService) {
        this.styledStringConverter = Objects.requireNonNull(styledStringConverter);
        this.defaultEMFLabelService = Objects.requireNonNull(defaultEMFLabelService);
    }

    @Override
    public boolean canHandle(EObject self) {
        // Simplified check: if the package name starts with "EDT" or "edt" it's likely ours given the context.
        // Or strictly check against known URIs.
        String nsURI = self.eClass().getEPackage().getNsURI();
        return nsURI != null && (EDT_NS_URIS.contains(nsURI) || nsURI.startsWith("EDT") || nsURI.startsWith("edt"));
    }

    @Override
    public StyledString getStyledLabel(EObject self) {
        // Check for Step labels first
        StyledString stepLabel = this.getStepLabel(self);
        if (stepLabel != null) {
            return stepLabel;
        }

        ComposedAdapterFactory adapterFactory = new ComposedAdapterFactory(ComposedAdapterFactory.Descriptor.Registry.INSTANCE);
        adapterFactory.addAdapterFactory(new EDTProjectItemProviderAdapterFactory());
        adapterFactory.addAdapterFactory(new EDTInterfaceItemProviderAdapterFactory());
        adapterFactory.addAdapterFactory(new EDTTypeItemProviderAdapterFactory());
        adapterFactory.addAdapterFactory(new EdtdeploymentItemProviderAdapterFactory());
        adapterFactory.addAdapterFactory(new EdtimplementationItemProviderAdapterFactory());
        adapterFactory.addAdapterFactory(new EdtlogicalItemProviderAdapterFactory());
        adapterFactory.addAdapterFactory(new EdtqosItemProviderAdapterFactory());
        adapterFactory.addAdapterFactory(new EdtbinItemProviderAdapterFactory());
        adapterFactory.addAdapterFactory(new EdtudpItemProviderAdapterFactory());
        adapterFactory.addAdapterFactory(new EdtuidItemProviderAdapterFactory());

        StyledString result = null;

        // First try IItemStyledLabelProvider for styled labels
        var styledAdapter = adapterFactory.adapt(self, IItemStyledLabelProvider.class);
        if (styledAdapter instanceof IItemStyledLabelProvider itemStyledLabelProvider) {
            var rawStyledString = itemStyledLabelProvider.getStyledText(self);
            if (rawStyledString instanceof org.eclipse.emf.edit.provider.StyledString emfStyledString) {
                result = this.styledStringConverter.convert(emfStyledString);
            }
        }

        // Fallback to IItemLabelProvider for plain text labels
        if (result == null) {
            var labelAdapter = adapterFactory.adapt(self, IItemLabelProvider.class);
            if (labelAdapter instanceof IItemLabelProvider itemLabelProvider) {
                String label = itemLabelProvider.getText(self);
                if (label != null && !label.isEmpty()) {
                    result = StyledString.of(label);
                }
            }
        }

        // Last resort: use default EMF label service
        if (result != null) {
            return result;
        }
        return this.defaultEMFLabelService.getStyledLabel(self);
    }

    /**
     * Get custom label for Step objects.
     */
    private StyledString getStepLabel(EObject self) {
        StyledString result = null;
        if (self instanceof Step0) {
            result = StyledString.of("0-Types");
        } else if (self instanceof Step1) {
            result = StyledString.of("1-Services");
        } else if (self instanceof Step2) {
            result = StyledString.of("2-ComponentDefinitions");
        } else if (self instanceof Step3) {
            result = StyledString.of("3-InitialAssembly");
        } else if (self instanceof Step4) {
            result = StyledString.of("4-ComponentImplementations");
        } else if (self instanceof Step5) {
            result = StyledString.of("5-Integration");
        }
        return result;
    }

    @Override
    public List<String> getImagePaths(EObject self) {
        return this.defaultEMFLabelService.getImagePaths(self);
    }
}

