import { ConfigInvalidaError, lerConfig } from './config';

const ORIGEM = 'http://localhost:4200';
const DOMINIO = 'https://sisgares-teste.auth.us-east-1.amazoncognito.com';

describe('lerConfig', () => {
  it('sem authModo ou com valor desconhecido usa o modo simulado', () => {
    expect(lerConfig(null, ORIGEM).authModo).toBe('simulado');
    expect(lerConfig({}, ORIGEM).authModo).toBe('simulado');
    expect(lerConfig({ authModo: 'simulado' }, ORIGEM).authModo).toBe('simulado');
    expect(lerConfig({ authModo: 'qualquer' }, ORIGEM).authModo).toBe('simulado');
  });

  it('modo cognito usa a origem do site como redirect e logout por padrão', () => {
    const c = lerConfig({ authModo: 'cognito', cognitoDomain: `${DOMINIO}/`, clientId: 'abc123' }, ORIGEM);
    expect(c).toEqual({
      authModo: 'cognito',
      cognitoDomain: DOMINIO,
      clientId: 'abc123',
      redirectUri: ORIGEM,
      logoutUri: ORIGEM,
      scopes: 'openid email',
    });
  });

  it('aceita redirect e logout explícitos', () => {
    const c = lerConfig(
      { authModo: 'cognito', cognitoDomain: DOMINIO, clientId: 'abc', redirectUri: 'https://app.exemplo.gov.br', logoutUri: 'https://app.exemplo.gov.br/saiu' },
      ORIGEM,
    );
    expect(c.redirectUri).toBe('https://app.exemplo.gov.br');
    expect(c.logoutUri).toBe('https://app.exemplo.gov.br/saiu');
  });

  it('modo cognito sem domínio ou client id é erro, nunca cai em simulado', () => {
    expect(() => lerConfig({ authModo: 'cognito', clientId: 'abc' }, ORIGEM)).toThrow(ConfigInvalidaError);
    expect(() => lerConfig({ authModo: 'cognito', cognitoDomain: DOMINIO }, ORIGEM)).toThrow(ConfigInvalidaError);
  });

  it('exige https no domínio, exceto localhost', () => {
    expect(() => lerConfig({ authModo: 'cognito', cognitoDomain: 'http://auth.exemplo.com', clientId: 'a' }, ORIGEM)).toThrow(ConfigInvalidaError);
    expect(() => lerConfig({ authModo: 'cognito', cognitoDomain: 'javascript:alert(1)', clientId: 'a' }, ORIGEM)).toThrow(ConfigInvalidaError);
    expect(lerConfig({ authModo: 'cognito', cognitoDomain: 'http://localhost:9000', clientId: 'a' }, ORIGEM).cognitoDomain).toBe('http://localhost:9000');
  });
});
