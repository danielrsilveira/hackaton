/** Datas sempre em horário local, sem fuso (formato da API: yyyy-MM-ddTHH:mm[:ss]). */

const p2 = (n: number) => String(n).padStart(2, '0');

export function paraData(s: string): Date {
  return new Date(s.length === 10 ? `${s}T00:00` : s);
}

export function isoData(d: Date): string {
  return `${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())}`;
}

export function isoDataHora(d: Date): string {
  return `${isoData(d)}T${p2(d.getHours())}:${p2(d.getMinutes())}`;
}

export function hora(s: string | Date): string {
  const d = typeof s === 'string' ? paraData(s) : s;
  return `${p2(d.getHours())}:${p2(d.getMinutes())}`;
}

export function dataHora(s: string): string {
  const d = paraData(s);
  return `${p2(d.getDate())}/${p2(d.getMonth() + 1)}/${d.getFullYear()} ${hora(d)}`;
}

const DIAS = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];

export function cabecalhoDia(d: Date): string {
  return `${DIAS[d.getDay()]} ${p2(d.getDate())}/${p2(d.getMonth() + 1)}`;
}

export function periodoTexto(inicio: string, termino: string): string {
  const a = paraData(inicio);
  const b = paraData(termino);
  return isoData(a) === isoData(b) ? `${dataHora(inicio)} – ${hora(b)}` : `${dataHora(inicio)} – ${dataHora(termino)}`;
}

/** Datas consecutivas a partir da referência, opcionalmente sem sábados e domingos. */
export function datasConsecutivas(referencia: string, quantidade: number, fimDeSemana: boolean): Date[] {
  const out: Date[] = [];
  const d = paraData(referencia);
  while (out.length < quantidade) {
    const dia = d.getDay();
    if (fimDeSemana || (dia !== 0 && dia !== 6)) {
      out.push(new Date(d));
    }
    d.setDate(d.getDate() + 1);
  }
  return out;
}

export function minutos(hhmm: string): number {
  const [h, m] = hhmm.split(':').map(Number);
  return h * 60 + m;
}

export function hoje(): string {
  return isoData(new Date());
}
