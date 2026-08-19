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

import React from 'react';
import { Navigate } from 'react-router-dom';
import { ViewerContextProviderProps, ViewerContextValue } from './ViewerContext.types';
import { useViewer } from './useViewer';
import { GQLViewer } from './useViewer.types';

const defaultValue: ViewerContextValue<GQLViewer> = {
  viewer: {
    id: '',
    capabilities: {
      projects: {
        canList: false,
        canCreate: false,
        canUpload: false,
      },
      libraries: {
        canList: false,
      },
    },
  },
};

export const ViewerContext = React.createContext<ViewerContextValue<GQLViewer>>(defaultValue);

export const ViewerContextProvider = ({ children }: ViewerContextProviderProps) => {
  const { data, loading, hasError } = useViewer();

  if (loading) {
    return null;
  }

  if (hasError || !data) {
    return <Navigate to="/login" replace />;
  }

  return <ViewerContext.Provider value={data}>{children}</ViewerContext.Provider>;
};
