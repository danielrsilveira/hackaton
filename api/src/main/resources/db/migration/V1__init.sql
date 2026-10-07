-- Modelo base do SISGARES (tabelas básicas, reservas e vínculos).
-- Nomes seguem as tabelas dos CSV de docs/dados (AMBI_, RECU_, ...), em snake_case.

create table unidade (
    id          bigint primary key,
    sigla       varchar(30)  not null,
    descricao   varchar(200) not null,
    hora_min    time,                       -- RN3: faixa da unidade macro prevalece sobre a global
    hora_max    time
);

create table configuracao (
    id               int primary key check (id = 1),
    antecedencia_min int          not null check (antecedencia_min >= 0),
    hora_min         time         not null,
    hora_max         time         not null,
    snp_endpoint     varchar(300) not null
);

create table envolvido (
    id            bigserial primary key,
    descricao     varchar(200) not null,
    email         varchar(200),             -- caixa postal padrão
    emails_lista  varchar(1000),            -- lista opcional (separada por ;) que substitui a padrão
    ativo         boolean not null default true,
    unidade_id    bigint references unidade (id)
);

create table usuario (
    id            bigserial primary key,
    nome          varchar(120) not null,
    email         varchar(200),
    perfil        varchar(20)  not null check (perfil in ('SOLICITANTE', 'ADMIN', 'ATENDENTE')),
    unidade_id    bigint not null references unidade (id),
    envolvido_id  bigint references envolvido (id)
);

create table ambiente (
    id          bigserial primary key,
    descricao   varchar(200) not null,
    ativo       boolean not null default true,
    id_pai      bigint references ambiente (id),
    unidade_id  bigint not null references unidade (id)
);

create table envolvido_ambiente (
    id               bigserial primary key,
    envo_id          bigint not null references envolvido (id),
    ambi_id          bigint not null references ambiente (id),
    cod_servico_snp  varchar(50),
    unique (envo_id, ambi_id)
);

create table disposicao (
    id             bigserial primary key,
    descricao      varchar(200) not null,
    ativo          boolean not null default true,
    icone_arquivo  varchar(200) not null
);

create table grupo_recurso (
    id         bigserial primary key,
    descricao  varchar(100) not null,
    ordem      int,
    ativo      boolean not null default true
);

create table recurso (
    id               bigserial primary key,
    descricao        varchar(200) not null,
    grec_id          bigint not null references grupo_recurso (id),
    limitado         boolean not null default false,
    disponibilidade  int not null default 0 check (disponibilidade >= 0),
    ativo            boolean not null default true,
    icone_arquivo    varchar(200) not null,
    unidade_id       bigint references unidade (id)   -- null = disponível em todas as unidades
);

create table envolvido_recurso (
    id               bigserial primary key,
    envo_id          bigint not null references envolvido (id),
    recu_id          bigint not null references recurso (id),
    cod_servico_snp  varchar(50),
    unique (envo_id, recu_id)
);

create table vinculo_recurso (
    id       bigserial primary key,
    recu_id  bigint not null references recurso (id),
    ambi_id  bigint not null references ambiente (id),
    unique (recu_id, ambi_id)
);

create table reserva (
    id                     bigserial primary key,
    unidade_id             bigint not null references unidade (id),
    solicitante_id         bigint not null references usuario (id),
    ambi_id                bigint references ambiente (id),     -- null = "Não solicitado / local próprio"
    complemento_ambiente   varchar(300),
    finalidade             text not null,
    qtd_participantes      int  not null check (qtd_participantes > 0),
    disp_id                bigint references disposicao (id),
    cancelada              boolean not null default false,
    dthr_cancelamento      timestamp,
    dthr_criacao           timestamp not null,
    dthr_ultima_alteracao  timestamp not null,
    versao                 int not null default 1
);

create table periodo_reserva (
    id            bigserial primary key,
    rese_id       bigint not null references reserva (id) on delete cascade,
    dthr_inicio   timestamp not null,
    dthr_termino  timestamp not null,
    check (dthr_termino > dthr_inicio)
);
create index ix_periodo_reserva_rese on periodo_reserva (rese_id);
create index ix_periodo_reserva_intervalo on periodo_reserva (dthr_inicio, dthr_termino);

create table solicitacao (
    id       bigserial primary key,
    rese_id  bigint not null references reserva (id) on delete cascade,
    recu_id  bigint not null references recurso (id),
    qtd      int check (qtd is null or qtd > 0)                 -- só para recursos limitados
);
create index ix_solicitacao_rese on solicitacao (rese_id);

create table notificacao (
    id             bigserial primary key,
    rese_id        bigint not null references reserva (id),
    envo_id        bigint not null references envolvido (id),
    destinatarios  varchar(1000) not null,
    tipo           varchar(20)  not null check (tipo in ('NOVA', 'ALTERADA', 'CANCELADA')),
    assunto        varchar(300) not null,
    html           text not null,
    dthr           timestamp not null
);

create table pedido_snp (
    id           bigserial primary key,
    rese_id      bigint not null references reserva (id),
    envo_id      bigint not null references envolvido (id),
    cod_servico  varchar(50)  not null,
    origem       varchar(200) not null,
    numero       varchar(30)  not null,
    url          varchar(300) not null,
    dthr         timestamp not null
);

-- Numeração do SNP simulado.
create sequence snp_numero_seq start with 2026000001;
