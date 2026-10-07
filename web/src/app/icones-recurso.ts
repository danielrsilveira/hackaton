/** Prefixo dos ícones enviados pelo administrador (espelha IconesRecurso.PREFIXO_ENVIADO na API). */
const PREFIXO_ENVIADO = 'up-';

/**
 * URL da imagem de um ícone de recurso: os do sistema são arquivos estáticos em /img/recurso;
 * os enviados pelo administrador vêm da API, sem extensão no nome.
 */
export function iconeUrl(arquivo: string | null | undefined): string {
  if (!arquivo) return '/img/recurso/indefinido.png';
  return arquivo.startsWith(PREFIXO_ENVIADO)
    ? `/api/icones-recurso/${encodeURIComponent(arquivo)}`
    : `/img/recurso/${arquivo}`;
}
