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
package org.eclipse.sirius.web.edt.configuration;

import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import edtbin.provider.EdtbinItemProviderAdapterFactory;
import edtdeployment.provider.EdtdeploymentItemProviderAdapterFactory;
import edtimplementation.provider.EdtimplementationItemProviderAdapterFactory;
import edtinterface.provider.EDTInterfaceItemProviderAdapterFactory;
import edtlogical.provider.EdtlogicalItemProviderAdapterFactory;
import edtproject.provider.EDTProjectItemProviderAdapterFactory;
import edtqos.provider.EdtqosItemProviderAdapterFactory;
import edttype.provider.EDTTypeItemProviderAdapterFactory;
import edtudp.provider.EdtudpItemProviderAdapterFactory;
import edtuid.provider.EdtuidItemProviderAdapterFactory;
import temp.provider.TempItemProviderAdapterFactory;

/**
 * Used to register everything to use EDT models in Sirius Web.
 *
 * @author EDT Team
 */
@Configuration
public class EdtEMFConfiguration {

    @Bean
    public ComposedAdapterFactory.Descriptor edtbinAdapterFactoryDescriptor() {
        return EdtbinItemProviderAdapterFactory::new;
    }

    @Bean
    public ComposedAdapterFactory.Descriptor edtdeploymentAdapterFactoryDescriptor() {
        return EdtdeploymentItemProviderAdapterFactory::new;
    }

    @Bean
    public ComposedAdapterFactory.Descriptor edtimplementationAdapterFactoryDescriptor() {
        return EdtimplementationItemProviderAdapterFactory::new;
    }

    @Bean
    public ComposedAdapterFactory.Descriptor edtinterfaceAdapterFactoryDescriptor() {
        return EDTInterfaceItemProviderAdapterFactory::new;
    }

    @Bean
    public ComposedAdapterFactory.Descriptor edtlogicalAdapterFactoryDescriptor() {
        return EdtlogicalItemProviderAdapterFactory::new;
    }

    @Bean
    public ComposedAdapterFactory.Descriptor edtprojectAdapterFactoryDescriptor() {
        return EDTProjectItemProviderAdapterFactory::new;
    }

    @Bean
    public ComposedAdapterFactory.Descriptor edtqosAdapterFactoryDescriptor() {
        return EdtqosItemProviderAdapterFactory::new;
    }

    @Bean
    public ComposedAdapterFactory.Descriptor edttypeAdapterFactoryDescriptor() {
        return EDTTypeItemProviderAdapterFactory::new;
    }

    @Bean
    public ComposedAdapterFactory.Descriptor edtudpAdapterFactoryDescriptor() {
        return EdtudpItemProviderAdapterFactory::new;
    }

    @Bean
    public ComposedAdapterFactory.Descriptor edtuidAdapterFactoryDescriptor() {
        return EdtuidItemProviderAdapterFactory::new;
    }

    @Bean
    public ComposedAdapterFactory.Descriptor tempAdapterFactoryDescriptor() {
        return TempItemProviderAdapterFactory::new;
    }
}
