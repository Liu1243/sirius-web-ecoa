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
package org.eclipse.sirius.web.edt.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

/**
 * Configuration used to retrieve the message source accessor for the project.
 *
 * @author tgiraudet
 */
@Configuration
public class EdtMessageServiceConfiguration {
    private static final String PATH = "messages/sirius-web-edt";

    @Bean
    public MessageSourceAccessor edtMessageSourceAccessor(@Value("${spring.mvc.locale:en_EN}") Locale locale) {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.addBasenames(PATH);
        messageSource.setDefaultLocale(Locale.ENGLISH);
        messageSource.setDefaultEncoding(null);
        return new MessageSourceAccessor(messageSource, locale);
    }
}
