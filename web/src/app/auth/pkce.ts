/** Primitivas do Authorization Code + PKCE (RFC 7636), só com a Web Crypto API do navegador. */

/** Base64 URL-safe, sem preenchimento. */
export function base64Url(bytes: Uint8Array): string {
  let bin = '';
  for (const b of bytes) {
    bin += String.fromCharCode(b);
  }
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Valor aleatório criptograficamente seguro (code_verifier, state, nonce). 32 bytes geram 43 caracteres. */
export function aleatorio(bytes = 32): string {
  const a = new Uint8Array(bytes);
  crypto.getRandomValues(a);
  return base64Url(a);
}

/** code_challenge (método S256) = BASE64URL(SHA-256(code_verifier)). Exige contexto seguro (https ou localhost). */
export async function desafioPkce(verifier: string): Promise<string> {
  if (!globalThis.crypto?.subtle) {
    throw new Error('Web Crypto indisponível: acesse o sistema por HTTPS.');
  }
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(hash));
}
