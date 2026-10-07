import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Ambiente, Api, Erro, Sessao, Setor, VinculoSetor, errosDaResposta } from '../api';
import { arvoreAmbientes, descendentes } from '../arvore-ambientes';
import { Icone } from '../icone';

/** Linha editável do vínculo setor × ambiente (RF03). */
interface LinhaSetor { chave: number; setorId: number | null; codServicoSnp: string; inativo: boolean; nomeInativo: string; }

/**
 * F9 / RF02: cadastro de ambientes com hierarquia (somente administrador).
 * Não há exclusão: o ambiente é inativado (reservas e vínculos continuam apontando para ele).
 */
@Component({
  selector: 'app-ambientes',
  imports: [FormsModule, Icone],
  template: `
    <div class="cabecalho-pagina">
      <div>
        <h2>Ambientes</h2>
        <p>Ambientes da unidade e sua hierarquia. Reservar um ambiente pai também ocupa os filhos (RN6).</p>
      </div>
      @if (sessao.admin()) {
        <button type="button" (click)="novo()"><app-icone nome="mais" /> Novo ambiente</button>
      }
    </div>

    @if (!sessao.admin()) {
      <p class="aviso"><app-icone nome="alerta" [tamanho]="20" /> Somente o administrador pode cadastrar ambientes.</p>
    } @else {
      <div class="grade">
        <section class="cartao" aria-labelledby="t-lista">
          <h3 id="t-lista" class="cartao-titulo"><app-icone nome="local" /> Ambientes cadastrados</h3>
          @if (!carregado()) {
            <p role="status">Carregando…</p>
          } @else if (!arvore().length) {
            <p>Nenhum ambiente cadastrado.</p>
          } @else {
            <div class="rolagem">
              <table class="dados">
                <caption class="visualmente-oculto">Ambientes, com os filhos logo abaixo do pai</caption>
                <thead><tr><th scope="col">Ambiente</th><th scope="col">Situação</th><th scope="col"><span class="visualmente-oculto">Ações</span></th></tr></thead>
                <tbody>
                  @for (n of arvore(); track n.ambiente.id) {
                    <tr [class.selecionado]="editandoId() === n.ambiente.id">
                      <td>
                        <span class="nome" [style.padding-left.rem]="n.nivel * 1.25">
                          @if (n.nivel > 0) { <span aria-hidden="true" class="ramo">└</span> }
                          {{ n.ambiente.descricao }}
                          @if (n.nivel > 0) { <span class="visualmente-oculto">(filho de {{ nomeDe(n.ambiente.idPai) }})</span> }
                        </span>
                      </td>
                      <td><span class="situacao" [class.inativo]="!n.ambiente.ativo">{{ n.ambiente.ativo ? 'Ativo' : 'Inativo' }}</span></td>
                      <td class="acoes-linha">
                        <button type="button" class="secundario" (click)="editar(n.ambiente)"
                                [attr.aria-label]="'Editar ' + n.ambiente.descricao">Editar</button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>

        <div class="coluna">
        <section class="cartao formulario" aria-labelledby="t-form">
          <h3 id="t-form" class="cartao-titulo">
            <app-icone [nome]="editandoId() == null ? 'mais' : 'ajustes'" />
            {{ editandoId() == null ? 'Novo ambiente' : 'Editar ambiente' }}
          </h3>
          @if (erros().length) {
            <div class="erros" role="alert"><app-icone nome="alerta" [tamanho]="20" />
              <ul>@for (e of erros(); track $index) { <li>{{ e.mensagem }}</li> }</ul></div>
          }
          @if (ok()) { <p class="sucesso" role="status"><app-icone nome="ok" [tamanho]="20" /> {{ ok() }}</p> }
          <form (ngSubmit)="salvar()">
            <label for="a-desc">Descrição <span class="obrigatorio">(obrigatório)</span></label>
            <input #descricaoInput id="a-desc" name="descricao" [(ngModel)]="descricao" required maxlength="200" />

            <label for="a-pai">Ambiente pai</label>
            <select id="a-pai" name="pai" [(ngModel)]="idPai" aria-describedby="h-pai">
              <option [ngValue]="null">Nenhum (ambiente principal)</option>
              @for (n of opcoesPai(); track n.ambiente.id) {
                <option [ngValue]="n.ambiente.id">{{ recuo(n.nivel) }}{{ n.ambiente.descricao }}{{ n.ambiente.ativo ? '' : ' (inativo)' }}</option>
              }
            </select>
            <span id="h-pai" class="ajuda">O próprio ambiente e seus filhos não aparecem, para evitar ciclos.</span>

            <label class="interruptor campo-ativo" for="a-ativo">
              <input id="a-ativo" type="checkbox" name="ativo" [(ngModel)]="ativo" aria-describedby="h-ativo" />
              Ativo (disponível para novas reservas)
            </label>
            <span id="h-ativo" class="ajuda">Só é possível inativar sem filhos ativos e sem reservas previstas ou em andamento.</span>

            <div class="acoes">
              @if (editandoId() != null) {
                <button type="button" class="secundario" (click)="novo()">Cancelar edição</button>
              }
              <button type="submit" [disabled]="salvando()"><app-icone nome="ok" /> Salvar</button>
            </div>
          </form>
        </section>

        @if (editandoId() != null) {
          <section class="cartao formulario setores" aria-labelledby="t-setores">
            <h3 id="t-setores" class="cartao-titulo" tabindex="-1"><app-icone nome="email" /> Setores notificados</h3>
            <p class="ajuda topo">
              Cada setor recebe e-mail das reservas de {{ nomeDe(editandoId()) }} (RN10). Com código de serviço, também é
              aberto um pedido no SNP (RN11). Vale a partir de agora: reservas já gravadas avisam os novos setores
              quando forem alteradas ou canceladas.
            </p>
            @if (errosSetores().length) {
              <div class="erros" role="alert"><app-icone nome="alerta" [tamanho]="20" />
                <ul>@for (e of errosSetores(); track $index) { <li>{{ e.mensagem }}</li> }</ul></div>
            }
            @if (okSetores()) { <p class="sucesso" role="status"><app-icone nome="ok" [tamanho]="20" /> Setores salvos.</p> }
            @if (carregandoSetores()) {
              <p role="status">Carregando setores…</p>
            } @else {
              <form (ngSubmit)="salvarSetores()">
                @if (!linhas.length) { <p>Nenhum setor vinculado: as reservas deste ambiente não geram e-mail por ele.</p> }
                @for (l of linhas; track l.chave; let i = $index) {
                  <fieldset class="linha-setor">
                    <legend class="visualmente-oculto">Setor {{ i + 1 }}</legend>
                    <div class="campo-setor">
                      <label [for]="'s-setor-' + l.chave">Setor</label>
                      <select [id]="'s-setor-' + l.chave" [name]="'setor-' + l.chave" [(ngModel)]="l.setorId" required>
                        <option [ngValue]="null" disabled>Escolha o setor</option>
                        @if (l.inativo) { <option [ngValue]="l.setorId">{{ l.nomeInativo }} (inativo)</option> }
                        @for (s of setores(); track s.id) {
                          <option [ngValue]="s.id" [disabled]="usadoEmOutraLinha(s.id, i)">{{ s.descricao }}</option>
                        }
                      </select>
                    </div>
                    <div>
                      <label [for]="'s-cod-' + l.chave">Código de serviço SNP <span class="obrigatorio">(opcional)</span></label>
                      <input [id]="'s-cod-' + l.chave" [name]="'cod-' + l.chave" [(ngModel)]="l.codServicoSnp" maxlength="50"
                             placeholder="ex.: SEG-0401" autocomplete="off" />
                    </div>
                    <button type="button" class="perigo icone" (click)="removerLinha(i)"
                            [attr.aria-label]="'Remover setor ' + (i + 1)"><app-icone nome="lixeira" /></button>
                  </fieldset>
                }
                <div class="acoes">
                  <button type="button" class="secundario" (click)="adicionarLinha()"><app-icone nome="mais" /> Adicionar setor</button>
                  <button type="submit" [disabled]="salvandoSetores()"><app-icone nome="ok" /> Salvar setores</button>
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
    .nome { display: inline-flex; gap: 0.4rem; align-items: baseline; }
    .ramo { color: var(--suave); }
    .situacao { font-weight: 600; font-size: 0.86rem; }
    .situacao.inativo { color: var(--suave); font-style: italic; }
    .acoes-linha { text-align: right; }
    tr.selecionado td { background: var(--primaria-100); }
    .formulario input, .formulario select { margin-bottom: 0.25rem; }
    .formulario .ajuda { margin-bottom: 1rem; }
    .campo-ativo { margin-top: 0.5rem; }
    .obrigatorio { font-weight: 400; color: var(--suave); }
    .acoes { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 0.5rem; margin-top: 1rem; }
    .ajuda.topo { margin: -0.5rem 0 1rem; }
    .linha-setor { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 0.5rem; align-items: end;
      padding-bottom: 0.75rem; margin-bottom: 0.75rem; border-bottom: 1px solid var(--linha); }
    .linha-setor input, .linha-setor select { margin-bottom: 0; }
    .linha-setor .campo-setor { grid-column: 1 / -1; }
    @media (max-width: 860px) {
      .grade { grid-template-columns: 1fr; }
      .coluna { order: -1; }
    }
  `,
})
export class Ambientes {
  private readonly api = inject(Api);
  protected readonly sessao = inject(Sessao);
  private readonly descricaoInput = viewChild<ElementRef<HTMLInputElement>>('descricaoInput');

  protected readonly ambientes = signal<Ambiente[]>([]);
  protected readonly carregado = signal(false);
  protected readonly editandoId = signal<number | null>(null);
  protected readonly erros = signal<Erro[]>([]);
  protected readonly ok = signal('');
  protected readonly salvando = signal(false);

  protected descricao = '';
  protected idPai: number | null = null;
  protected ativo = true;

  // RF03: setores notificados do ambiente em edição
  protected readonly setores = signal<Setor[]>([]);
  protected readonly carregandoSetores = signal(false);
  protected readonly salvandoSetores = signal(false);
  protected readonly errosSetores = signal<Erro[]>([]);
  protected readonly okSetores = signal(false);
  protected linhas: LinhaSetor[] = [];
  private proximaChave = 0;

  protected readonly arvore = computed(() => arvoreAmbientes(this.ambientes()));

  /** Pai possível: ativo (ou o pai atual), nunca o próprio ambiente nem um descendente. */
  protected readonly opcoesPai = computed(() => {
    const id = this.editandoId();
    const proibidos = id == null ? new Set<number>() : new Set([id, ...descendentes(id, this.ambientes())]);
    const paiAtual = id == null ? null : this.ambientes().find((a) => a.id === id)?.idPai ?? null;
    return this.arvore().filter((n) => !proibidos.has(n.ambiente.id) && (n.ambiente.ativo || n.ambiente.id === paiAtual));
  });

  constructor() {
    this.carregar();
    this.api.setores().subscribe((s) => this.setores.set(s));
  }

  private carregarSetores(ambienteId: number): void {
    this.carregandoSetores.set(true);
    this.errosSetores.set([]);
    this.okSetores.set(false);
    this.api.setoresDoAmbiente(ambienteId).subscribe({
      next: (vs) => {
        this.linhas = vs.map((v) => this.linha(v));
        this.carregandoSetores.set(false);
      },
      error: (e) => {
        this.errosSetores.set(errosDaResposta(e));
        this.carregandoSetores.set(false);
      },
    });
  }

  private linha(v?: VinculoSetor): LinhaSetor {
    return {
      chave: this.proximaChave++,
      setorId: v?.setorId ?? null,
      codServicoSnp: v?.codServicoSnp ?? '',
      inativo: v != null && !v.setorAtivo,
      nomeInativo: v?.setor ?? '',
    };
  }

  protected usadoEmOutraLinha(setorId: number, indice: number): boolean {
    return this.linhas.some((l, i) => i !== indice && l.setorId === setorId);
  }

  protected adicionarLinha(): void {
    this.linhas = [...this.linhas, this.linha()];
    this.okSetores.set(false);
    const chave = this.linhas[this.linhas.length - 1].chave;
    setTimeout(() => document.getElementById(`s-setor-${chave}`)?.focus());
  }

  protected removerLinha(indice: number): void {
    this.linhas = this.linhas.filter((_, i) => i !== indice);
    this.okSetores.set(false);
    // Mantém o foco no cartão para quem navega por teclado.
    setTimeout(() => document.getElementById('t-setores')?.focus());
  }

  protected salvarSetores(): void {
    const id = this.editandoId();
    if (id == null) return;
    this.errosSetores.set([]);
    this.okSetores.set(false);
    this.salvandoSetores.set(true);
    const dados = this.linhas.map((l) => ({ setorId: l.setorId, codServicoSnp: l.codServicoSnp.trim() || null }));
    this.api.salvarSetoresDoAmbiente(id, dados).subscribe({
      next: (vs) => {
        this.linhas = vs.map((v) => this.linha(v));
        this.salvandoSetores.set(false);
        this.okSetores.set(true);
      },
      error: (e) => {
        this.salvandoSetores.set(false);
        this.errosSetores.set(errosDaResposta(e));
      },
    });
  }

  private carregar(): void {
    this.api.ambientesTodos().subscribe({
      next: (as) => {
        this.ambientes.set(as);
        this.carregado.set(true);
      },
      error: (e) => {
        this.erros.set(errosDaResposta(e));
        this.carregado.set(true);
      },
    });
  }

  protected nomeDe(id: number | null): string {
    return this.ambientes().find((a) => a.id === id)?.descricao ?? '';
  }

  protected recuo(nivel: number): string {
    return '\u00a0\u00a0'.repeat(nivel * 2);
  }

  protected novo(): void {
    this.editandoId.set(null);
    this.descricao = '';
    this.idPai = null;
    this.ativo = true;
    this.erros.set([]);
    this.ok.set('');
    this.focarDescricao();
  }

  protected editar(a: Ambiente): void {
    this.editandoId.set(a.id);
    this.descricao = a.descricao;
    this.idPai = a.idPai;
    this.ativo = a.ativo;
    this.erros.set([]);
    this.ok.set('');
    this.carregarSetores(a.id);
    this.focarDescricao();
  }

  protected salvar(): void {
    const dados = { descricao: this.descricao.trim(), idPai: this.idPai, ativo: this.ativo };
    if (!dados.descricao) {
      this.erros.set([{ regra: 'RF02', mensagem: 'Informe a descrição do ambiente.' }]);
      return;
    }
    const id = this.editandoId();
    this.erros.set([]);
    this.ok.set('');
    this.salvando.set(true);
    (id == null ? this.api.criarAmbiente(dados) : this.api.alterarAmbiente(id, dados)).subscribe({
      next: (a) => {
        this.salvando.set(false);
        this.ambientes.update((as) => [...as.filter((x) => x.id !== a.id), a]);
        if (id == null) {
          // Ambiente recém-criado: libera o cartão de setores, ainda vazio.
          this.linhas = [];
          this.errosSetores.set([]);
          this.okSetores.set(false);
        }
        this.editandoId.set(a.id);
        this.ok.set(id == null ? `Ambiente "${a.descricao}" criado.` : `Ambiente "${a.descricao}" salvo.`);
      },
      error: (e) => {
        this.salvando.set(false);
        this.erros.set(errosDaResposta(e));
      },
    });
  }

  private focarDescricao(): void {
    // Após a renderização do título/estado do formulário.
    setTimeout(() => this.descricaoInput()?.nativeElement.focus());
  }
}
