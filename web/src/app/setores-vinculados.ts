import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { Api, Erro, Setor, VinculoSetor, VinculoSetorInput, errosDaResposta } from './api';
import { Icone } from './icone';

/** Linha editável do vínculo com setor. */
interface LinhaSetor { chave: number; setorId: number | null; codServicoSnp: string; inativo: boolean; nomeInativo: string; }

let sequencia = 0;

/**
 * Cartão "Setores notificados" de um ambiente (RF03) ou recurso (RF07): cada setor recebe e-mail (RN10)
 * e, com código de serviço, gera pedido no SNP (RN11). Salvar substitui a lista inteira.
 */
@Component({
  selector: 'app-setores-vinculados',
  imports: [FormsModule, Icone],
  template: `
    <section class="cartao" [attr.aria-labelledby]="prefixo + '-titulo'">
      <h3 [id]="prefixo + '-titulo'" class="cartao-titulo" tabindex="-1"><app-icone nome="email" /> Setores notificados</h3>
      <p class="ajuda topo">
        Cada setor recebe e-mail das reservas {{ alvo() === 'ambiente' ? 'de' : 'que pedem' }} {{ nome() }} (RN10).
        Com código de serviço, também é aberto um pedido no SNP (RN11). Vale a partir de agora: reservas já gravadas
        avisam os novos setores quando forem alteradas ou canceladas.
      </p>
      @if (erros().length) {
        <div class="erros" role="alert"><app-icone nome="alerta" [tamanho]="20" />
          <ul>@for (e of erros(); track $index) { <li>{{ e.mensagem }}</li> }</ul></div>
      }
      @if (ok()) { <p class="sucesso" role="status"><app-icone nome="ok" [tamanho]="20" /> Setores salvos.</p> }
      @if (carregando()) {
        <p role="status">Carregando setores…</p>
      } @else {
        <form (ngSubmit)="salvar()">
          @if (!linhas.length) {
            <p>Nenhum setor vinculado: {{ alvo() === 'ambiente' ? 'este ambiente' : 'este recurso' }} não gera e-mail.</p>
          }
          @for (l of linhas; track l.chave; let i = $index) {
            <fieldset class="linha-setor">
              <legend class="visualmente-oculto">Setor {{ i + 1 }}</legend>
              <div class="campo-setor">
                <label [for]="prefixo + '-setor-' + l.chave">Setor</label>
                <select [id]="prefixo + '-setor-' + l.chave" [name]="'setor-' + l.chave" [(ngModel)]="l.setorId" required>
                  <option [ngValue]="null" disabled>Escolha o setor</option>
                  @if (l.inativo) { <option [ngValue]="l.setorId">{{ l.nomeInativo }} (inativo)</option> }
                  @for (s of setores(); track s.id) {
                    <option [ngValue]="s.id" [disabled]="usadoEmOutraLinha(s.id, i)">{{ s.descricao }}</option>
                  }
                </select>
              </div>
              <div>
                <label [for]="prefixo + '-cod-' + l.chave">Código de serviço SNP <span class="obrigatorio">(opcional)</span></label>
                <input [id]="prefixo + '-cod-' + l.chave" [name]="'cod-' + l.chave" [(ngModel)]="l.codServicoSnp"
                       maxlength="50" placeholder="ex.: TI-0101" autocomplete="off" />
              </div>
              <button type="button" class="perigo icone" (click)="remover(i)"
                      [attr.aria-label]="'Remover setor ' + (i + 1)"><app-icone nome="lixeira" /></button>
            </fieldset>
          }
          <div class="acoes">
            <button type="button" class="secundario" (click)="adicionar()"><app-icone nome="mais" /> Adicionar setor</button>
            <button type="submit" [disabled]="salvando()"><app-icone nome="ok" /> Salvar setores</button>
          </div>
        </form>
      }
    </section>
  `,
  styles: `
    .ajuda.topo { margin: -0.5rem 0 1rem; }
    .obrigatorio { font-weight: 400; color: var(--suave); }
    .linha-setor { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 0.5rem; align-items: end;
      padding-bottom: 0.75rem; margin-bottom: 0.75rem; border-bottom: 1px solid var(--linha); }
    .linha-setor .campo-setor { grid-column: 1 / -1; }
    .acoes { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 0.5rem; margin-top: 1rem; }
  `,
})
export class SetoresVinculados {
  private readonly api = inject(Api);

  readonly alvo = input.required<'ambiente' | 'recurso'>();
  readonly alvoId = input.required<number>();
  readonly nome = input('');

  protected readonly prefixo = `sv${++sequencia}`;
  protected readonly setores = signal<Setor[]>([]);
  protected readonly carregando = signal(false);
  protected readonly salvando = signal(false);
  protected readonly erros = signal<Erro[]>([]);
  protected readonly ok = signal(false);
  protected linhas: LinhaSetor[] = [];
  private proximaChave = 0;

  constructor() {
    this.api.setores().subscribe((s) => this.setores.set(s));
    // Recarrega sempre que o ambiente/recurso em edição muda.
    effect(() => {
      const [alvo, id] = [this.alvo(), this.alvoId()];
      untracked(() => this.carregar(alvo, id));
    });
  }

  private ler(alvo: 'ambiente' | 'recurso', id: number): Observable<VinculoSetor[]> {
    return alvo === 'ambiente' ? this.api.setoresDoAmbiente(id) : this.api.setoresDoRecurso(id);
  }

  private gravar(id: number, vs: VinculoSetorInput[]): Observable<VinculoSetor[]> {
    return this.alvo() === 'ambiente' ? this.api.salvarSetoresDoAmbiente(id, vs) : this.api.salvarSetoresDoRecurso(id, vs);
  }

  private carregar(alvo: 'ambiente' | 'recurso', id: number): void {
    this.carregando.set(true);
    this.erros.set([]);
    this.ok.set(false);
    this.ler(alvo, id).subscribe({
      next: (vs) => {
        this.linhas = vs.map((v) => this.linha(v));
        this.carregando.set(false);
      },
      error: (e) => {
        this.erros.set(errosDaResposta(e));
        this.carregando.set(false);
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

  protected adicionar(): void {
    this.linhas = [...this.linhas, this.linha()];
    this.ok.set(false);
    const chave = this.linhas[this.linhas.length - 1].chave;
    setTimeout(() => document.getElementById(`${this.prefixo}-setor-${chave}`)?.focus());
  }

  protected remover(indice: number): void {
    this.linhas = this.linhas.filter((_, i) => i !== indice);
    this.ok.set(false);
    // Mantém o foco no cartão para quem navega por teclado.
    setTimeout(() => document.getElementById(`${this.prefixo}-titulo`)?.focus());
  }

  protected salvar(): void {
    const id = this.alvoId();
    this.erros.set([]);
    this.ok.set(false);
    this.salvando.set(true);
    const dados = this.linhas.map((l) => ({ setorId: l.setorId, codServicoSnp: l.codServicoSnp.trim() || null }));
    this.gravar(id, dados).subscribe({
      next: (vs) => {
        this.linhas = vs.map((v) => this.linha(v));
        this.salvando.set(false);
        this.ok.set(true);
      },
      error: (e) => {
        this.salvando.set(false);
        this.erros.set(errosDaResposta(e));
      },
    });
  }
}
