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

import DeployedInstanceIcon from '../../../static/icon/24x24/deployed.PNG';
import PlatformIcon from '../../../static/icon/24x24/logical.PNG';
import NodeIcon from '../../../static/icon/24x24/node.PNG';
import ProtectionDomainIcon from '../../../static/icon/24x24/protection.PNG';
import { LegendItem } from './CompositeDiagramLegend.types';

const EDT_NATURE = 'siriusComponents://nature?kind=edt';

export interface LogicalSystemDiagramLegendProps {}

const legendItems: LegendItem[] = [
  { label: 'Logical Computing Platform', iconPath: PlatformIcon },
  { label: 'Logical Computing Node', iconPath: NodeIcon },
  { label: 'Protection Domain', iconPath: ProtectionDomainIcon },
  { label: 'Deployed Instance', iconPath: DeployedInstanceIcon },
  { label: 'Logical Computing Platform Link', isLine: true, color: '#607D8B' },
  { label: 'Logical Computing Node Link', isLine: true, color: '#795548' },
];

export const LogicalSystemDiagramLegend = memo(({}: LogicalSystemDiagramLegendProps) => {
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
                <img src={item.iconPath} alt={t(item.label)} style={{ width: '16px', height: '16px' }} />
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
