import { aleatorio, base64Url, desafioPkce } from './pkce';

describe('PKCE', () => {
  it('gera o code_challenge do vetor de teste do RFC 7636 (apêndice B)', async () => {
    const verifier = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk';
    expect(await desafioPkce(verifier)).toBe('E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM');
  });

  it('base64Url usa o alfabeto URL-safe e não deixa preenchimento', () => {
    expect(base64Url(new Uint8Array([251, 255, 254]))).toBe('-__-');
    expect(base64Url(new Uint8Array([102]))).toBe('Zg');
  });

  it('aleatorio produz 43 caracteres URL-safe para 32 bytes e não repete', () => {
    const a = aleatorio(32);
    const b = aleatorio(32);
    expect(a).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(a).not.toBe(b);
  });
});
