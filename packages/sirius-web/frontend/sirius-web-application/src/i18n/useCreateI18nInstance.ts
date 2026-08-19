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

import i18next, { i18n, InitOptions } from 'i18next';
import HttpBackend from 'i18next-http-backend';
import { useMemo, useState } from 'react';
import { initReactI18next } from 'react-i18next';
import { en } from './locales/en';
import { enTrees } from './locales/trees_en';
import { zhTrees } from './locales/trees_zh';
import { zh } from './locales/zh';
import { tablesEn } from './locales/tables_en';
import { tablesZh } from './locales/tables_zh';
import { UseCreateI18nInstanceValue } from './useCreateI18nInstance.types';

export const useCreateI18nInstance = (
  language: string | null,
  namespaces: string[],
  httpOrigin: string
): UseCreateI18nInstanceValue => {
  const [loading, setLoading] = useState<boolean>(true);

  const effectiveNamespaces = namespaces.length > 0 ? namespaces : ['sirius-web-application'];

  const i18nextOptions: InitOptions = {
    lng: language ?? undefined,
    ns: effectiveNamespaces,
    defaultNS: effectiveNamespaces[0],
    fallbackLng: 'zh',
    preload: language ? [language] : undefined,
    supportedLngs: ['en', 'fr', 'zh'],
    backend: {
      loadPath: `${httpOrigin}/api/locales/{{lng}}/{{ns}}.json`,
    },
    partialBundledLanguages: true,
    resources: {
      en: {
        'sirius-web-application': en,
        'sirius-components-trees': enTrees,
        'sirius-components-tables': tablesEn,
      },
      zh: {
        'sirius-web-application': zh,
        'sirius-components-trees': zhTrees,
        'sirius-components-tables': tablesZh,
      },
    },
    interpolation: {
      escapeValue: false,
    },
    react: {
      useSuspense: false,
    },
  };

  const i18nInstance: i18n | null = useMemo(() => {
    if (!language) {
      return null;
    }
    setLoading(true);
    const instance = i18next.createInstance();
    instance
      .use(HttpBackend)
      .use(initReactI18next)
      .init(i18nextOptions, () => setLoading(false));
    return instance;
  }, [httpOrigin, language, effectiveNamespaces.join(',')]);

  return { data: i18nInstance, loading };
};
