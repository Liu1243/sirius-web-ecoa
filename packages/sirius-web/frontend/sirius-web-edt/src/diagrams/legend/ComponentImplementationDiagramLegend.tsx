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
import { LegendItem } from './CompositeDiagramLegend.types';

const EDT_NATURE = 'siriusComponents://nature?kind=edt';

// Define the legend items following the provided style requirements
const legendItems: LegendItem[] = [
  { label: 'Writer (Data)', color: '#9E9E9E', isLine: false },
  { label: 'Reader (Data)', color: '#BDBDBD', isLine: false },
  { label: 'Client (Request)', color: '#FDD835', isLine: false },
  { label: 'Server (Request)', color: '#FFEE58', isLine: false },
  { label: 'Sender (Event)', color: '#90CAF9', isLine: false },
  { label: 'Receiver (Event)', color: '#BBDEFB', isLine: false },
  { label: 'DataLink', isLine: true, color: 'black' },
  { label: 'RequestLink', isLine: true, color: '#E65100' },
  { label: 'EventLink', isLine: true, color: '#1565C0' },
];

export const ComponentImplementationDiagramLegend = memo(() => {
  const { project } = useCurrentProject();
  const { t } = useTranslation('sirius-web-application');

  if (
    !project.natures ||
    project.natures.filter((nature: { name: string }) => nature.name === EDT_NATURE).length === 0
  ) {
    return null;
  }

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
                <Box
                  sx={{
                    width: '16px',
                    height: '16px',
                    backgroundColor: item.color,
                    border: '1px solid #757575',
                    borderRadius: '2px',
                  }}
                />
              )}
              <Typography variant="body2" sx={{ fontSize: '12px' }}>
                {t(item.label)}
              </Typography>
            </Box>
          ))}
        </Box>
      </Paper>
    </Panel>
  );
});
