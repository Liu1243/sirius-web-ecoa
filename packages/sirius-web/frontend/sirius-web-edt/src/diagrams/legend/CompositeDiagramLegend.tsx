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

import { useCurrentProject } from '@eclipse-sirius/sirius-web-application';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { Panel } from '@xyflow/react';
import { memo } from 'react';
import { useTranslation } from 'react-i18next';
import { CompositeDiagramLegendProps, LegendItem } from './CompositeDiagramLegend.types';

import ComponentIcon from '../../../static/icon/component.png';
import PropertyIcon from '../../../static/icon/component_property.png';
import ReferenceIcon from '../../../static/icon/component_reference.png';
import ServiceIcon from '../../../static/icon/component_service.png';

const EDT_NATURE = 'siriusComponents://nature?kind=edt';

export const CompositeDiagramLegend = memo(({}: CompositeDiagramLegendProps) => {
  const { project } = useCurrentProject();
  const { t } = useTranslation('sirius-web-application');

  if (project.natures.filter((nature: { name: string }) => nature.name === EDT_NATURE).length === 0) {
    return null;
  }

  const legendItems: LegendItem[] = [
    { label: t('Component'), iconPath: ComponentIcon },
    { label: t('Service'), iconPath: ServiceIcon },
    { label: t('Reference'), iconPath: ReferenceIcon },
    { label: t('Property'), iconPath: PropertyIcon },
    { label: t('ServiceLink'), isLine: true, color: 'black' },
  ];

  return (
    <Panel position="top-right">
      <Paper
        elevation={3}
        sx={{
          padding: '8px 12px',
          backgroundColor: 'rgba(255, 255, 255, 0.95)',
          borderRadius: '8px',
          minWidth: '120px',
        }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 'bold', marginBottom: '4px' }}>
          {t('Legend')}
        </Typography>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
          {legendItems.map((item) => (
            <Box key={item.label} sx={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              {item.isLine ? (
                <Box sx={{ width: '16px', height: '2px', backgroundColor: item.color || 'black' }} />
              ) : (
                <img
                  src={item.iconPath}
                  alt={item.label}
                  style={{ width: '16px', height: '16px', filter: item.filter }}
                />
              )}
              <Typography variant="body2" sx={{ fontSize: '12px' }}>
                {item.label}
              </Typography>
            </Box>
          ))}
        </Box>
      </Paper>
    </Panel>
  );
});
