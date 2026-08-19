import { ComponentCodeHistory, ComponentCodeTag } from './ComponentHistoryView.types';

export interface UseComponentCodeHistoryState {
  history: ComponentCodeHistory | null;
  tags: ComponentCodeTag[];
  loading: boolean;
  error: Error | null;
}

export interface UseComponentCodeHistoryValue {
  state: UseComponentCodeHistoryState;
  refresh: () => void;
  deleteVersion: (versionId: string) => Promise<boolean>;
  deleteVersions: (versionIds: string[]) => Promise<{ success: string[]; failed: string[] }>;
}

export interface GQLGetComponentCodeHistoryData {
  data: {
    componentCodeHistory: {
      history: ComponentCodeHistory;
    };
  };
}

export interface GQLGetComponentCodeTagsData {
  data: {
    componentCodeTags: {
      tags: ComponentCodeTag[];
    };
  };
}
