export function createClientId(): string {
  const cryptoApi = globalThis.crypto;

  if (cryptoApi && typeof cryptoApi.randomUUID === 'function') {
    return cryptoApi.randomUUID();
  }

  return `cf-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
