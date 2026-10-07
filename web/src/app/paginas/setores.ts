import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, Erro, SetorCadastro, Sessao, errosDaResposta } from '../api';
import { Icone } from '../icone';

/**
 * F9 / RF01: cadastro de setores envolvidos (somente administrador).
 * Não há exclusão: o setor é inativado e deixa de receber e-mails e pedidos SNP.
 */
@Component({
  selector: 'app-setores',
  imports: [FormsModule, Icone],
  template: `
    <div class="cabecalho-pagina">
      <div>
        <h2>Setores envolvidos</h2>
        <p>Setores que recebem e-mail das reservas dos ambientes e recursos vinculados a eles (RN10).</p>
      </div>
      @if (sessao.admin()) {
        <button type="button" (click)="novo()"><app-icone nome="mais" /> Novo setor</button>
      }
    </div>

    @if (!sessao.admin()) {
      <p class="aviso"><app-icone nome="alerta" [tamanho]="20" /> Somente o administrador pode cadastrar setores.</p>
    } @else {
      <div class="grade">
        <section class="cartao" aria-labelledby="t-lista">
          <h3 id="t-lista" class="cartao-titulo"><app-icone nome="grupo" /> Setores cadastrados</h3>
          @if (!carregado()) {
            <p role="status">Carregando…</p>
          } @else if (!ordenados().length) {
            <p>Nenhum setor cadastrado.</p>
          } @else {
            <div class="rolagem">
              <table class="dados">
                <caption class="visualmente-oculto">Setores da unidade, com destino dos e-mails e vínculos</caption>
                <thead><tr>
                  <th scope="col">Setor e destino dos e-mails</th><th scope="col">Vínculos</th>
                  <th scope="col">Situação</th><th scope="col"><span class="visualmente-oculto">Ações</span></th>
                </tr></thead>
                <tbody>
                  @for (s of ordenados(); track s.id) {
                    <tr [class.selecionado]="editandoId() === s.id">
                      <td>
                        {{ s.descricao }}
                        <span class="ajuda destino">
                          {{ s.emailsLista ? 'Lista:' : 'Caixa padrão:' }} {{ s.emailsLista || s.email }}
                        </span>
                      </td>
                      <td class="numero">{{ rotuloVinculos(s) }}</td>
                      <td><span class="situacao" [class.inativo]="!s.ativo">{{ s.ativo ? 'Ativo' : 'Inativo' }}</span></td>
                      <td class="acoes-linha">
                        <button type="button" class="secundario" (click)="editar(s)"
                                [attr.aria-label]="'Editar ' + s.descricao">Editar</button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>

        <section class="cartao formulario" aria-labelledby="t-form">
          <h3 id="t-form" class="cartao-titulo">
            <app-icone [nome]="editandoId() == null ? 'mais' : 'ajustes'" />
            {{ editandoId() == null ? 'Novo setor' : 'Editar setor' }}
          </h3>
          @if (erros().length) {
            <div class="erros" role="alert"><app-icone nome="alerta" [tamanho]="20" />
              <ul>@for (e of erros(); track $index) { <li>{{ e.mensagem }}</li> }</ul></div>
          }
          @if (ok()) { <p class="sucesso" role="status"><app-icone nome="ok" [tamanho]="20" /> {{ ok() }}</p> }
          <form (ngSubmit)="salvar()">
            <label for="s-desc">Descrição <span class="obrigatorio">(obrigatório)</span></label>
            <input #descricaoInput id="s-desc" name="descricao" [(ngModel)]="descricao" required maxlength="200" />

            <label for="s-email">Caixa postal padrão <span class="obrigatorio">(obrigatório)</span></label>
            <input id="s-email" name="email" type="email" [(ngModel)]="email" required maxlength="200"
                   autocomplete="off" placeholder="setor@exemplo.gov.br" />

            <label for="s-lista">Lista de e-mails <span class="obrigatorio">(opcional)</span></label>
            <textarea id="s-lista" name="lista" [(ngModel)]="emailsLista" rows="3" aria-describedby="h-lista"></textarea>
            <span id="h-lista" class="ajuda">
              Separe os endereços por ponto e vírgula, vírgula ou quebra de linha. Quando preenchida, substitui a caixa postal padrão.
            </span>

            <label class="interruptor campo-ativo" for="s-ativo">
              <input id="s-ativo" type="checkbox" name="ativo" [(ngModel)]="ativo" aria-describedby="h-ativo" />
              Ativo (recebe notificações)
            </label>
            <span id="h-ativo" class="ajuda">
              Setor inativo deixa de receber e-mails e pedidos SNP; os vínculos com ambientes e recursos são mantidos.
            </span>

            <div class="acoes">
              @if (editandoId() != null) {
                <button type="button" class="secundario" (click)="novo()">Cancelar edição</button>
              }
              <button type="submit" [disabled]="salvando()"><app-icone nome="ok" /> Salvar</button>
            </div>
          </form>
        </section>
      </div>
    }
  `,
  styles: `
    .grade { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); gap: 0 1rem; align-items: start; }
    .destino { overflow-wrap: anywhere; }
    .numero { min-width: 7rem; }
    .situacao { font-weight: 600; font-size: 0.86rem; }
    .situacao.inativo { color: var(--suave); font-style: italic; }
    .acoes-linha { text-align: right; }
    tr.selecionado td { background: var(--primaria-100); }
    .formulario input, .formulario textarea { margin-bottom: 1rem; }
    .formulario textarea { margin-bottom: 0.25rem; min-height: 4.5rem; }
    .formulario .ajuda { margin-bottom: 1rem; }
    .campo-ativo { margin-top: 0.5rem; }
    .obrigatorio { font-weight: 400; color: var(--suave); }
    .acoes { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 0.5rem; margin-top: 1rem; }
    @media (max-width: 860px) { .grade { grid-template-columns: 1fr; } .formulario { order: -1; } }
  `,
})
export class Setores {
  private readonly api = inject(Api);
  protected readonly sessao = inject(Sessao);
  private readonly descricaoInput = viewChild<ElementRef<HTMLInputElement>>('descricaoInput');

  protected readonly setores = signal<SetorCadastro[]>([]);
  protected readonly carregado = signal(false);
  protected readonly editandoId = signal<number | null>(null);
  protected readonly erros = signal<Erro[]>([]);
  protected readonly ok = signal('');
  protected readonly salvando = signal(false);

  protected descricao = '';
  protected email = '';
  protected emailsLista = '';
  protected ativo = true;

  protected readonly ordenados = computed(() =>
    [...this.setores()].sort((a, b) => a.descricao.localeCompare(b.descricao, 'pt-BR')),
  );

  constructor() {
    this.api.setoresCadastro().subscribe({
      next: (ss) => {
        this.setores.set(ss);
        this.carregado.set(true);
      },
      error: (e) => {
        this.erros.set(errosDaResposta(e));
        this.carregado.set(true);
      },
    });
  }

  protected rotuloVinculos(s: SetorCadastro): string {
    const partes = [];
    if (s.ambientes) partes.push(`${s.ambientes} ambiente${s.ambientes > 1 ? 's' : ''}`);
    if (s.recursos) partes.push(`${s.recursos} recurso${s.recursos > 1 ? 's' : ''}`);
    return partes.length ? partes.join(', ') : 'Nenhum';
  }

  protected novo(): void {
    this.editandoId.set(null);
    this.descricao = '';
    this.email = '';
    this.emailsLista = '';
    this.ativo = true;
    this.erros.set([]);
    this.ok.set('');
    this.focarDescricao();
  }

  protected editar(s: SetorCadastro): void {
    this.editandoId.set(s.id);
    this.descricao = s.descricao;
    this.email = s.email ?? '';
    this.emailsLista = (s.emailsLista ?? '').split(/;\s*/).filter(Boolean).join('\n');
    this.ativo = s.ativo;
    this.erros.set([]);
    this.ok.set('');
    this.focarDescricao();
  }

  protected salvar(): void {
    const dados = {
      descricao: this.descricao.trim(),
      email: this.email.trim(),
      emailsLista: this.emailsLista.trim() || null,
      ativo: this.ativo,
    };
    const id = this.editandoId();
    this.erros.set([]);
    this.ok.set('');
    this.salvando.set(true);
    (id == null ? this.api.criarSetor(dados) : this.api.alterarSetor(id, dados)).subscribe({
      next: (s) => {
        this.salvando.set(false);
        this.setores.update((ss) => [...ss.filter((x) => x.id !== s.id), s]);
        this.editar(s);
        this.ok.set(id == null ? `Setor "${s.descricao}" criado.` : `Setor "${s.descricao}" salvo.`);
      },
      error: (e) => {
        this.salvando.set(false);
        this.erros.set(errosDaResposta(e));
      },
    });
  }

  private focarDescricao(): void {
    setTimeout(() => this.descricaoInput()?.nativeElement.focus());
  }
}
