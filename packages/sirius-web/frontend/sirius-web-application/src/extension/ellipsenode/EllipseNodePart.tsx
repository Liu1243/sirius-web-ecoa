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

import {
  AppearanceColorPicker,
  AppearanceNumberTextfield,
  AppearanceSelect,
  DiagramContext,
  DiagramContextValue,
  useResetNodeAppearance,
} from '@eclipse-sirius/sirius-components-diagrams';
import LineStyleIcon from '@mui/icons-material/LineStyle';
import LineWeightIcon from '@mui/icons-material/LineWeight';
import Box from '@mui/material/Box';
import ListItem from '@mui/material/ListItem';
import Typography from '@mui/material/Typography';
import { useContext } from 'react';
import { useTranslation } from 'react-i18next';
import { EllipseNodePartProps } from './EllipseNodePart.types';
import { useUpdateEllipseNodeAppearance } from './useUpdateEllipseNodeAppearance';
import { GQLEllipseNodeAppearanceInput } from './useUpdateEllipseNodeAppearance.types';

export const EllipseNodePart = ({ nodeIds, style, customizedStyleProperties }: EllipseNodePartProps) => {
  const { editingContextId, diagramId } = useContext<DiagramContextValue>(DiagramContext);
  const { updateEllipseNodeAppearance } = useUpdateEllipseNodeAppearance();
  const { resetNodeStyleProperties } = useResetNodeAppearance();
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'ellipseNodeStyle' });

  const lineStyleOptions = [
    { value: 'Solid', label: t('solid') },
    { value: 'Dash', label: t('dash') },
    { value: 'Dot', label: t('dot') },
    { value: 'Dash_Dot', label: t('dashDot') },
  ];

  const handleResetProperty = (customizedStyleProperty: string) =>
    resetNodeStyleProperties(editingContextId, diagramId, nodeIds, [customizedStyleProperty]);

  const handleEditProperty = (newValue: Partial<GQLEllipseNodeAppearanceInput>) =>
    updateEllipseNodeAppearance(editingContextId, diagramId, nodeIds, newValue);

  const isDisabled = (property: string) => !customizedStyleProperties.includes(property);

  return (
    <ListItem disablePadding sx={(theme) => ({ paddingX: theme.spacing(1), paddingBottom: theme.spacing(1) })}>
      <Box sx={{ display: 'flex', flexDirection: 'column' }}>
        <Typography variant="subtitle2">{t('style')}</Typography>

        <AppearanceColorPicker
          label={t('background')}
          initialValue={style.background}
          disabled={isDisabled('BACKGROUND')}
          onEdit={(newValue) => handleEditProperty({ background: newValue })}
          onReset={() => handleResetProperty('BACKGROUND')}></AppearanceColorPicker>

        <AppearanceColorPicker
          label={t('borderColor')}
          initialValue={style.borderColor}
          disabled={isDisabled('BORDER_COLOR')}
          onEdit={(newValue) => handleEditProperty({ borderColor: newValue })}
          onReset={() => handleResetProperty('BORDER_COLOR')}></AppearanceColorPicker>

        <AppearanceNumberTextfield
          icon={<LineWeightIcon />}
          label={t('borderSize')}
          initialValue={style.borderSize}
          disabled={isDisabled('BORDER_SIZE')}
          onEdit={(newValue) => handleEditProperty({ borderSize: newValue })}
          onReset={() => handleResetProperty('BORDER_SIZE')}></AppearanceNumberTextfield>

        <AppearanceSelect
          icon={<LineStyleIcon />}
          label={t('borderLineStyle')}
          options={lineStyleOptions}
          initialValue={style.borderStyle}
          disabled={isDisabled('BORDER_STYLE')}
          onEdit={(newValue) => handleEditProperty({ borderStyle: newValue })}
          onReset={() => handleResetProperty('BORDER_STYLE')}></AppearanceSelect>
      </Box>
    </ListItem>
  );
};
