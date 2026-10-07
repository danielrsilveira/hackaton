import { iconeUrl } from './icones-recurso';

describe('iconeUrl', () => {
  it('usa o arquivo estático para os ícones do sistema', () => {
    expect(iconeUrl('equip_005.png')).toBe('/img/recurso/equip_005.png');
  });

  it('busca na API os ícones enviados pelo administrador', () => {
    expect(iconeUrl('up-12')).toBe('/api/icones-recurso/up-12');
  });

  it('cai no ícone indefinido sem arquivo', () => {
    expect(iconeUrl(null)).toBe('/img/recurso/indefinido.png');
  });
});
