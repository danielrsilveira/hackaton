import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Ambiente, AmbienteDoRecurso, Api, Erro, GrupoRecurso, RecursoCadastro, Sessao, errosDaResposta } from '../api';
import { arvoreAmbientes } from '../arvore-ambientes';
import { Icone } from '../icone';
import { iconeUrl } from '../icones-recurso';
import { SetoresVinculados } from '../setores-vinculados';

/**
 * F9 / RF06–RF08: cadastro de recursos, setores notificados e ambientes em que o recurso pode ser pedido
 * (somente administrador). Não há exclusão: o recurso é inativado.
 */
@Component({
  selector: 'app-recursos',
  imports: [FormsModule, Icone, SetoresVinculados],
  template: `
    <div class="cabecalho-pagina">
      <div>
        <h2>Recursos e serviços</h2>
        <p>O que pode ser pedido numa reserva. Recurso limitado tem quantidade controlada (RN8).</p>
      </div>
      @if (sessao.admin()) {
        <button type="button" (click)="novo()"><app-icone nome="mais" /> Novo recurso</button>
      }
    </div>

    @if (!sessao.admin()) {
      <p class="aviso"><app-icone nome="alerta" [tamanho]="20" /> Somente o administrador pode cadastrar recursos.</p>
    } @else {
      <div class="grade">
        <section class="cartao" aria-labelledby="t-lista">
          <h3 id="t-lista" class="cartao-titulo"><app-icone nome="pacote" /> Recursos cadastrados</h3>
          @if (!carregado()) {
            <p role="status">Carregando…</p>
          } @else if (!recursos().length) {
            <p>Nenhum recurso cadastrado.</p>
          } @else {
            <div class="rolagem">
              <table class="dados">
                <caption class="visualmente-oculto">Recursos agrupados, com oferta, vínculos, quantidade e situação</caption>
                <thead><tr>
                  <th scope="col">Recurso, oferta e vínculos</th><th scope="col">Quantidade</th>
                  <th scope="col">Situação</th><th scope="col"><span class="visualmente-oculto">Ações</span></th>
                </tr></thead>
                @for (g of porGrupo(); track g.grupo) {
                  <tbody>
                    <tr class="grupo"><th scope="rowgroup" colspan="4">{{ g.grupo }}</th></tr>
                    @for (r of g.itens; track r.id) {
                      <tr [class.selecionado]="editandoId() === r.id">
                        <td>
                          <span class="nome">
                            <img [src]="iconeUrl(r.iconeArquivo)" alt="" width="20" height="20" />
                            {{ r.descricao }}
                          </span>
                          <span class="ajuda">
                            {{ r.unidadeId == null ? 'Todas as unidades' : 'Somente esta unidade' }} · {{ rotuloVinculos(r) }}
                          </span>
                        </td>
                        <td>{{ r.limitado ? r.disponibilidade + ' disponível(is)' : 'Sem limite' }}</td>
                        <td><span class="situacao" [class.inativo]="!r.ativo">{{ r.ativo ? 'Ativo' : 'Inativo' }}</span></td>
                        <td class="acoes-linha">
                          <button type="button" class="secundario" (click)="editar(r)"
                                  [attr.aria-label]="'Editar ' + r.descricao">Editar</button>
                        </td>
                      </tr>
                    }
                  </tbody>
                }
              </table>
            </div>
          }
        </section>

        <div class="coluna">
          <section class="cartao formulario" aria-labelledby="t-form">
            <h3 id="t-form" class="cartao-titulo">
              <app-icone [nome]="editandoId() == null ? 'mais' : 'ajustes'" />
              {{ editandoId() == null ? 'Novo recurso' : 'Editar recurso' }}
            </h3>
            @if (erros().length) {
              <div class="erros" role="alert"><app-icone nome="alerta" [tamanho]="20" />
                <ul>@for (e of erros(); track $index) { <li>{{ e.mensagem }}</li> }</ul></div>
            }
            @if (ok()) { <p class="sucesso" role="status"><app-icone nome="ok" [tamanho]="20" /> {{ ok() }}</p> }
            <form (ngSubmit)="salvar()">
              <label for="r-desc">Descrição <span class="obrigatorio">(obrigatório)</span></label>
              <input #descricaoInput id="r-desc" name="descricao" [(ngModel)]="descricao" required maxlength="200" />

              <label for="r-grupo">Grupo <span class="obrigatorio">(obrigatório)</span></label>
              <select id="r-grupo" name="grupo" [(ngModel)]="grupoId" required>
                <option [ngValue]="null" disabled>Escolha o grupo</option>
                @for (g of grupos(); track g.id) {
                  @if (g.ativo || g.id === grupoId) {
                    <option [ngValue]="g.id">{{ g.descricao }}{{ g.ativo ? '' : ' (inativo)' }}</option>
                  }
                }
              </select>

              <fieldset class="icones">
                <legend class="rotulo">Ícone <span class="obrigatorio">(obrigatório)</span></legend>
                <div class="grade-icones">
                  @for (i of icones(); track i; let n = $index) {
                    <label class="opcao-icone" [class.marcado]="icone === i" [title]="i">
                      <input type="radio" name="icone" [value]="i" [attr.data-icone]="i" [(ngModel)]="icone" />
                      <img [src]="iconeUrl(i)" alt="" width="28" height="28" />
                      <span class="visualmente-oculto">Ícone {{ n + 1 }} ({{ i }})</span>
                    </label>
                  }
                  <button type="button" class="secundario novo-icone" (click)="arquivoIcone.click()"
                          [disabled]="enviandoIcone()" aria-describedby="h-icone">
                    <app-icone nome="mais" /> {{ enviandoIcone() ? 'Enviando…' : 'Novo ícone' }}
                  </button>
                </div>
                <label for="r-arquivo-icone" class="visualmente-oculto">Arquivo do novo ícone</label>
                <input #arquivoIcone id="r-arquivo-icone" type="file" class="visualmente-oculto" tabindex="-1"
                       accept="image/png,image/jpeg,image/gif" (change)="enviarIcone(arquivoIcone)" />
                <span id="h-icone" class="ajuda">Novo ícone: PNG, JPEG ou GIF, até 100 KB, de 16 a 512 pixels (de preferência quadrado).</span>
                @if (errosIcone().length) {
                  <div class="erros" role="alert"><app-icone nome="alerta" [tamanho]="20" />
                    <ul>@for (e of errosIcone(); track $index) { <li>{{ e.mensagem }}</li> }</ul></div>
                }
                @if (okIcone()) { <p class="sucesso" role="status"><app-icone nome="ok" [tamanho]="20" /> Ícone enviado e selecionado.</p> }
              </fieldset>

              <label class="interruptor" for="r-limitado">
                <input id="r-limitado" type="checkbox" name="limitado" [(ngModel)]="limitado" aria-describedby="h-limitado" />
                Disponibilidade limitada
              </label>
              <span id="h-limitado" class="ajuda">Só recursos limitados mostram o campo de quantidade na reserva (RN8).</span>
              @if (limitado) {
                <label for="r-disp">Quantidade disponível na unidade <span class="obrigatorio">(obrigatório)</span></label>
                <input id="r-disp" name="disp" type="number" min="1" max="9999" [(ngModel)]="disponibilidade" required
                       aria-describedby="h-disp" />
                <span id="h-disp" class="ajuda">Não pode ficar abaixo do que reservas previstas já pedem no mesmo horário.</span>
              }

              <label for="r-unidade">Oferecido em</label>
              <select id="r-unidade" name="unidade" [(ngModel)]="unidadeId" aria-describedby="h-unidade">
                <option [ngValue]="null">Todas as unidades</option>
                <option [ngValue]="sessao.usuario()?.unidadeId">Somente esta unidade</option>
              </select>
              <span id="h-unidade" class="ajuda">Recurso de uma unidade só é oferecido nela (RN9).</span>

              <label class="interruptor" for="r-ativo">
                <input id="r-ativo" type="checkbox" name="ativo" [(ngModel)]="ativo" aria-describedby="h-ativo" />
                Ativo (pode ser pedido)
              </label>
              <span id="h-ativo" class="ajuda">Só é possível inativar sem reservas previstas ou em andamento que o pedem.</span>

              <div class="acoes">
                @if (editandoId() != null) {
                  <button type="button" class="secundario" (click)="novo()">Cancelar edição</button>
                }
                <button type="submit" [disabled]="salvando()"><app-icone nome="ok" /> Salvar</button>
              </div>
            </form>
          </section>

          @if (editandoId(); as id) {
            <app-setores-vinculados alvo="recurso" [alvoId]="id" [nome]="nomeDe(id)" />

            <section class="cartao" aria-labelledby="t-amb">
              <h3 id="t-amb" class="cartao-titulo"><app-icone nome="local" /> Ambientes onde pode ser pedido</h3>
              <p class="ajuda topo">
                Sem nenhum marcado, o recurso pode ser pedido em qualquer ambiente e também sem ambiente. Marcando algum,
                só é oferecido nas reservas desses ambientes (RN9).
              </p>
              @if (errosAmb().length) {
                <div class="erros" role="alert"><app-icone nome="alerta" [tamanho]="20" />
                  <ul>@for (e of errosAmb(); track $index) { <li>{{ e.mensagem }}</li> }</ul></div>
              }
              @if (okAmb()) { <p class="sucesso" role="status"><app-icone nome="ok" [tamanho]="20" /> Ambientes salvos.</p> }
              @if (carregandoAmb()) {
                <p role="status">Carregando ambientes…</p>
              } @else {
                <form (ngSubmit)="salvarAmbientes()">
                  <fieldset>
                    <legend class="visualmente-oculto">Ambientes em que {{ nomeDe(id) }} pode ser pedido</legend>
                    <ul class="lista-amb">
                      @for (n of opcoesAmbiente(); track n.ambiente.id) {
                        <li [style.padding-left.rem]="n.nivel * 1.25">
                          <label class="opcao-amb" [for]="'r-amb-' + n.ambiente.id">
                            <input type="checkbox" [id]="'r-amb-' + n.ambiente.id" [name]="'amb-' + n.ambiente.id"
                                   [ngModel]="marcados().has(n.ambiente.id)" (ngModelChange)="marcar(n.ambiente.id, $event)" />
                            {{ n.ambiente.descricao }}{{ n.ambiente.ativo ? '' : ' (inativo)' }}
                          </label>
                        </li>
                      }
                    </ul>
                  </fieldset>
                  <p class="ajuda">{{ marcados().size ? marcados().size + ' ambiente(s) marcado(s).' : 'Nenhum marcado: sem restrição.' }}</p>
                  <div class="acoes">
                    <button type="submit" [disabled]="salvandoAmb()"><app-icone nome="ok" /> Salvar ambientes</button>
                  </div>
                </form>
              }
            </section>
          }
        </div>
      </div>
    }
  `,
  styles: `
    .grade { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); gap: 0 1rem; align-items: start; }
    .nome { display: inline-flex; gap: 0.5rem; align-items: center; }
    tr.grupo th { background: #f8fafc; color: var(--tinta); font-size: 0.8rem; }
    .situacao { font-weight: 600; font-size: 0.86rem; }
    .situacao.inativo { color: var(--suave); font-style: italic; }
    .acoes-linha { text-align: right; }
    tr.selecionado td { background: var(--primaria-100); }
    .formulario input:not([type='checkbox']):not([type='radio']), .formulario select { margin-bottom: 1rem; }
    .formulario .ajuda { margin: 0.25rem 0 1rem; }
    .obrigatorio { font-weight: 400; color: var(--suave); }
    .icones { margin-bottom: 1rem; }
    .grade-icones { display: flex; flex-wrap: wrap; gap: 0.4rem; }
    .opcao-icone { position: relative; display: inline-flex; padding: 0.35rem; margin: 0; border: 2px solid var(--linha);
      border-radius: var(--raio); cursor: pointer; background: var(--superficie); }
    .opcao-icone input { position: absolute; opacity: 0; width: 1px; height: 1px; }
    .opcao-icone.marcado { border-color: var(--primaria); background: var(--primaria-100); }
    .opcao-icone.marcado::after { content: '✓'; position: absolute; top: -0.5rem; right: -0.45rem; font-size: 0.7rem;
      background: var(--primaria); color: #fff; border-radius: 50%; width: 1rem; height: 1rem; display: grid; place-items: center; }
    .opcao-icone:has(input:focus-visible) { box-shadow: var(--foco); }
    .opcao-icone img { object-fit: contain; }
    .novo-icone { min-height: 2.9rem; font-size: 0.86rem; }
    .ajuda.topo { margin: -0.5rem 0 1rem; }
    .lista-amb { list-style: none; margin: 0; padding: 0; max-height: 18rem; overflow-y: auto; }
    .lista-amb li { padding-block: 0.3rem; }
    .opcao-amb { display: inline-flex; gap: 0.5rem; align-items: center; font-weight: 400; margin: 0; cursor: pointer; }
    .acoes { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 0.5rem; margin-top: 1rem; }
    @media (max-width: 860px) { .grade { grid-template-columns: 1fr; } .coluna { order: -1; } }
  `,
})
export class Recursos {
  protected readonly iconeUrl = iconeUrl;
  private readonly api = inject(Api);
  protected readonly sessao = inject(Sessao);
  private readonly descricaoInput = viewChild<ElementRef<HTMLInputElement>>('descricaoInput');

  protected readonly recursos = signal<RecursoCadastro[]>([]);
  protected readonly grupos = signal<GrupoRecurso[]>([]);
  protected readonly icones = signal<string[]>([]);
  protected readonly carregado = signal(false);
  protected readonly editandoId = signal<number | null>(null);
  protected readonly erros = signal<Erro[]>([]);
  protected readonly ok = signal('');
  protected readonly salvando = signal(false);

  protected descricao = '';
  protected grupoId: number | null = null;
  protected icone: string | null = null;
  protected limitado = false;
  protected disponibilidade: number | null = null;
  protected unidadeId: number | null = null;
  protected ativo = true;

  // RF06: envio de novo ícone
  protected readonly enviandoIcone = signal(false);
  protected readonly errosIcone = signal<Erro[]>([]);
  protected readonly okIcone = signal(false);

  // RF08: ambientes do recurso em edição
  private readonly ambientes = signal<Ambiente[]>([]);
  private readonly vinculados = signal<AmbienteDoRecurso[]>([]);
  protected readonly marcados = signal<Set<number>>(new Set());
  protected readonly carregandoAmb = signal(false);
  protected readonly salvandoAmb = signal(false);
  protected readonly errosAmb = signal<Erro[]>([]);
  protected readonly okAmb = signal(false);

  protected readonly porGrupo = computed(() => {
    const mapa = new Map<string, RecursoCadastro[]>();
    for (const r of this.recursos()) mapa.set(r.grupo, [...(mapa.get(r.grupo) ?? []), r]);
    return [...mapa.entries()].map(([grupo, itens]) => ({ grupo, itens }));
  });

  /** Ambientes ativos da unidade e os inativos já vinculados, em árvore. */
  protected readonly opcoesAmbiente = computed(() => {
    const ja = new Set(this.vinculados().map((v) => v.ambienteId));
    return arvoreAmbientes(this.ambientes()).filter((n) => n.ambiente.ativo || ja.has(n.ambiente.id));
  });

  constructor() {
    this.carregar();
    this.api.gruposRecurso().subscribe((g) => this.grupos.set(g));
    this.api.iconesRecurso().subscribe((i) => this.icones.set(i));
    this.api.ambientesTodos().subscribe((a) => this.ambientes.set(a));
  }

  private carregar(): void {
    this.api.recursosCadastro().subscribe({
      next: (rs) => {
        this.recursos.set([...rs].sort(ordem));
        this.carregado.set(true);
      },
      error: (e) => {
        this.erros.set(errosDaResposta(e));
        this.carregado.set(true);
      },
    });
  }

  protected nomeDe(id: number): string {
    return this.recursos().find((r) => r.id === id)?.descricao ?? '';
  }

  protected rotuloVinculos(r: RecursoCadastro): string {
    const setores = r.setores ? `${r.setores} setor${r.setores > 1 ? 'es' : ''}` : 'Sem setor';
    const ambientes = r.ambientes ? `${r.ambientes} ambiente${r.ambientes > 1 ? 's' : ''}` : 'qualquer ambiente';
    return `${setores} · ${ambientes}`;
  }

  protected novo(): void {
    this.editandoId.set(null);
    this.descricao = '';
    this.grupoId = null;
    this.icone = null;
    this.limitado = false;
    this.disponibilidade = null;
    this.unidadeId = null;
    this.ativo = true;
    this.erros.set([]);
    this.ok.set('');
    this.limparIcone();
    this.focarDescricao();
  }

  protected editar(r: RecursoCadastro): void {
    this.editandoId.set(r.id);
    this.descricao = r.descricao;
    this.grupoId = r.grupoId;
    this.icone = r.iconeArquivo;
    this.limitado = r.limitado;
    this.disponibilidade = r.limitado ? r.disponibilidade : null;
    this.unidadeId = r.unidadeId;
    this.ativo = r.ativo;
    this.erros.set([]);
    this.ok.set('');
    this.limparIcone();
    this.carregarAmbientes(r.id);
    this.focarDescricao();
  }

  private limparIcone(): void {
    this.errosIcone.set([]);
    this.okIcone.set(false);
  }

  /** RF06: envia o arquivo escolhido; o novo ícone entra na grade já selecionado. */
  protected enviarIcone(campo: HTMLInputElement): void {
    const arquivo = campo.files?.[0];
    campo.value = ''; // permite escolher o mesmo arquivo de novo após um erro
    if (!arquivo) return;
    this.errosIcone.set([]);
    this.okIcone.set(false);
    if (arquivo.size > 100 * 1024) {
      this.errosIcone.set([{ regra: 'RF06', mensagem: 'O ícone deve ter até 100 KB.' }]);
      return;
    }
    this.enviandoIcone.set(true);
    this.api.enviarIconeRecurso(arquivo).subscribe({
      next: ({ arquivo: nome }) => {
        this.enviandoIcone.set(false);
        this.icones.update((is) => [...is, nome]);
        this.icone = nome;
        this.okIcone.set(true);
        // Leva o foco ao ícone recém-criado, já marcado.
        setTimeout(() => (document.querySelector(`input[data-icone="${nome}"]`) as HTMLInputElement | null)?.focus());
      },
      error: (e) => {
        this.enviandoIcone.set(false);
        this.errosIcone.set(errosDaResposta(e));
      },
    });
  }

  protected salvar(): void {
    const dados = {
      descricao: this.descricao.trim(), grupoId: this.grupoId, limitado: this.limitado,
      disponibilidade: this.limitado ? this.disponibilidade : null, iconeArquivo: this.icone,
      unidadeId: this.unidadeId, ativo: this.ativo,
    };
    const id = this.editandoId();
    this.erros.set([]);
    this.ok.set('');
    this.salvando.set(true);
    (id == null ? this.api.criarRecurso(dados) : this.api.alterarRecurso(id, dados)).subscribe({
      next: (r) => {
        this.salvando.set(false);
        this.recursos.update((rs) => [...rs.filter((x) => x.id !== r.id), r].sort(ordem));
        if (id == null) this.carregarAmbientes(r.id);
        this.editandoId.set(r.id);
        this.ok.set(id == null ? `Recurso "${r.descricao}" criado.` : `Recurso "${r.descricao}" salvo.`);
      },
      error: (e) => {
        this.salvando.set(false);
        this.erros.set(errosDaResposta(e));
      },
    });
  }

  private carregarAmbientes(id: number): void {
    this.carregandoAmb.set(true);
    this.errosAmb.set([]);
    this.okAmb.set(false);
    this.api.ambientesDoRecurso(id).subscribe({
      next: (vs) => this.aplicarVinculos(vs),
      error: (e) => {
        this.errosAmb.set(errosDaResposta(e));
        this.carregandoAmb.set(false);
      },
    });
  }

  private aplicarVinculos(vs: AmbienteDoRecurso[]): void {
    this.vinculados.set(vs);
    const uni = this.sessao.usuario()?.unidadeId;
    this.marcados.set(new Set(vs.filter((v) => v.unidadeId === uni).map((v) => v.ambienteId)));
    this.carregandoAmb.set(false);
  }

  protected marcar(id: number, marcado: boolean): void {
    const s = new Set(this.marcados());
    if (marcado) s.add(id);
    else s.delete(id);
    this.marcados.set(s);
    this.okAmb.set(false);
  }

  protected salvarAmbientes(): void {
    const id = this.editandoId();
    if (id == null) return;
    this.errosAmb.set([]);
    this.okAmb.set(false);
    this.salvandoAmb.set(true);
    this.api.salvarAmbientesDoRecurso(id, [...this.marcados()]).subscribe({
      next: (vs) => {
        this.aplicarVinculos(vs);
        this.salvandoAmb.set(false);
        this.okAmb.set(true);
        this.recursos.update((rs) => rs.map((r) => (r.id === id ? { ...r, ambientes: vs.length } : r)));
      },
      error: (e) => {
        this.salvandoAmb.set(false);
        this.errosAmb.set(errosDaResposta(e));
      },
    });
  }

  private focarDescricao(): void {
    setTimeout(() => this.descricaoInput()?.nativeElement.focus());
  }
}

/** Mesma ordem da API: grupo (posição), depois descrição. */
function ordem(a: RecursoCadastro, b: RecursoCadastro): number {
  const ga = a.grupoOrdem ?? Number.MAX_SAFE_INTEGER;
  const gb = b.grupoOrdem ?? Number.MAX_SAFE_INTEGER;
  return ga - gb || a.grupo.localeCompare(b.grupo, 'pt-BR') || a.descricao.localeCompare(b.descricao, 'pt-BR');
}
