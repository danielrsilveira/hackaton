import { Ambiente } from './api';
import { arvoreAmbientes, descendentes } from './arvore-ambientes';

const amb = (id: number, descricao: string, idPai: number | null = null, ativo = true): Ambiente => ({ id, descricao, idPai, ativo });

const DADOS: Ambiente[] = [
  amb(3, 'Sala 1'),
  amb(6, 'Auditório (Parte B)', 1),
  amb(1, 'Auditório (Completo)'),
  amb(5, 'Auditório (Parte A)', 1),
  amb(8, 'Cabine', 5, false),
];

describe('arvoreAmbientes', () => {
  it('lista cada pai seguido dos filhos, em ordem alfabética e com o nível', () => {
    expect(arvoreAmbientes(DADOS).map((n) => [n.ambiente.id, n.nivel])).toEqual([
      [1, 0], [5, 1], [8, 2], [6, 1], [3, 0],
    ]);
  });

  it('trata como raiz o ambiente cujo pai não está na lista', () => {
    expect(arvoreAmbientes([amb(9, 'Órfão', 99)])).toEqual([{ ambiente: amb(9, 'Órfão', 99), nivel: 0 }]);
  });

  it('não entra em loop com ciclo nos dados', () => {
    const ciclo = [amb(1, 'A', 2), amb(2, 'B', 1)];
    expect(arvoreAmbientes(ciclo).map((n) => n.ambiente.id).sort()).toEqual([1, 2]);
  });
});

describe('descendentes', () => {
  it('inclui filhos e netos, mas não o próprio ambiente', () => {
    expect([...descendentes(1, DADOS)].sort()).toEqual([5, 6, 8]);
    expect(descendentes(3, DADOS).size).toBe(0);
  });
});
