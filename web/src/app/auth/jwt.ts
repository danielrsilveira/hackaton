/**
 * Leitura dos claims do ID token SOMENTE para agendar a renovação e conferir o nonce.
 * A assinatura NÃO é verificada aqui (o token veio direto do endpoint de token do Cognito, por TLS);
 * quem valida assinatura, issuer, audience e expiração é a API, a cada requisição.
 */
export function claimsDoJwt(token: string): Record<string, unknown> | null {
  const partes = token.split('.');
  if (partes.length !== 3) {
    return null;
  }
  try {
    const b64 = partes[1].replace(/-/g, '+').replace(/_/g, '/');
    const bin = atob(b64.padEnd(b64.length + ((4 - (b64.length % 4)) % 4), '='));
    const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0));
    const obj: unknown = JSON.parse(new TextDecoder().decode(bytes));
    return obj !== null && typeof obj === 'object' && !Array.isArray(obj) ? (obj as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

/** Instante de expiração (ms desde a época) ou 0 se o token for ilegível, o que força a renovação. */
export function expiracaoMs(token: string): number {
  const exp = claimsDoJwt(token)?.['exp'];
  return typeof exp === 'number' ? exp * 1000 : 0;
}
