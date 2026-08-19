const createUuidFromRandomValues = (randomValues: Uint8Array): string => {
  randomValues[6] = (randomValues[6] & 0x0f) | 0x40;
  randomValues[8] = (randomValues[8] & 0x3f) | 0x80;

  const hex = Array.from(randomValues, (value) => value.toString(16).padStart(2, '0'));

  return `${hex.slice(0, 4).join('')}-${hex.slice(4, 6).join('')}-${hex.slice(6, 8).join('')}-${hex
    .slice(8, 10)
    .join('')}-${hex.slice(10, 16).join('')}`;
};

const createRandomUUID = (): string => {
  if (typeof globalThis.crypto?.getRandomValues === 'function') {
    return createUuidFromRandomValues(globalThis.crypto.getRandomValues(new Uint8Array(16)));
  }

  const fallbackValues = new Uint8Array(16);
  for (let index = 0; index < fallbackValues.length; index++) {
    fallbackValues[index] = Math.floor(Math.random() * 256);
  }

  return createUuidFromRandomValues(fallbackValues);
};

if (typeof globalThis.crypto === 'object' && globalThis.crypto && typeof globalThis.crypto.randomUUID !== 'function') {
  Object.defineProperty(globalThis.crypto, 'randomUUID', {
    configurable: true,
    writable: true,
    value: createRandomUUID,
  });
}
