import { Ambiente } from './api';

export interface NoAmbiente { ambiente: Ambiente; nivel: number; }

const porDescricao = (a: Ambiente, b: Ambiente) => a.descricao.localeCompare(b.descricao, 'pt-BR');

/**
 * Lista em pré-ordem (pai seguido dos filhos), com o nível de cada ambiente para indentação.
 * Ambientes cujo pai não está na lista viram raízes; ciclos não causam loop.
 */
export function arvoreAmbientes(ambientes: Ambiente[]): NoAmbiente[] {
  const ids = new Set(ambientes.map((a) => a.id));
  const filhos = new Map<number | null, Ambiente[]>();
  for (const a of ambientes) {
    const pai = a.idPai != null && ids.has(a.idPai) && a.idPai !== a.id ? a.idPai : null;
    filhos.set(pai, [...(filhos.get(pai) ?? []), a]);
  }
  const saida: NoAmbiente[] = [];
  const visitados = new Set<number>();
  const visitar = (a: Ambiente, nivel: number) => {
    if (visitados.has(a.id)) return;
    visitados.add(a.id);
    saida.push({ ambiente: a, nivel });
    for (const f of [...(filhos.get(a.id) ?? [])].sort(porDescricao)) visitar(f, nivel + 1);
  };
  for (const raiz of [...(filhos.get(null) ?? [])].sort(porDescricao)) visitar(raiz, 0);
  // Sobras só existem se houver ciclo nos dados: mostra assim mesmo, como raízes.
  for (const a of [...ambientes].sort(porDescricao)) visitar(a, 0);
  return saida;
}

/** Ids dos descendentes (filhos, netos...) de um ambiente. */
export function descendentes(id: number, ambientes: Ambiente[]): Set<number> {
  const r = new Set<number>();
  const fila = [id];
  while (fila.length) {
    const atual = fila.shift()!;
    for (const a of ambientes) {
      if (a.idPai === atual && a.id !== id && !r.has(a.id)) {
        r.add(a.id);
        fila.push(a.id);
      }
    }
  }
  return r;
}
