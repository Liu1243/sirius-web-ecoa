/*******************************************************************************
 * Copyright (c) 2024 Dassault Aviation.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Dassault Aviation - initial API and implementation
 *******************************************************************************/

export interface ImportEcoaStepsMenuItemProps {
  project: { id: string };
  onClick: () => void;
}

export interface ImportEcoaStepsMenuItemState {
  dialogOpen: boolean;
  uploading: boolean;
  message: string | null;
  success: boolean | null;
}
