import { describe, expect, it } from 'vitest';
import { en } from './en';
import { zh } from './zh';

const componentImplementationLegendLabels = [
  'Writer (Data)',
  'Reader (Data)',
  'Client (Request)',
  'Server (Request)',
  'Sender (Event)',
  'Receiver (Event)',
  'DataLink',
  'RequestLink',
  'EventLink',
] as const;

describe('Component Implementation Diagram legend locales', () => {
  it('contains all legend labels in English', () => {
    componentImplementationLegendLabels.forEach((label) => {
      expect(en[label]).toBeDefined();
      expect(en[label]).not.toBe('');
    });
  });

  it('contains translated legend labels in Chinese', () => {
    componentImplementationLegendLabels.forEach((label) => {
      expect(zh[label]).toBeDefined();
      expect(zh[label]).not.toBe('');
      expect(zh[label]).not.toBe(label);
    });
  });
});
