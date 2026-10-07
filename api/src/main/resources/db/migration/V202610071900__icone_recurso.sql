-- RF06: ícones de recurso enviados pelo administrador (os do sistema ficam em web/public/img/recurso).
-- O nome em recurso.icone_arquivo é "up-<id>", servido por GET /api/icones-recurso/{arquivo}.
create table icone_recurso (
    id            bigserial primary key,
    arquivo       varchar(200) not null unique,
    tipo          varchar(50)  not null check (tipo in ('image/png', 'image/jpeg', 'image/gif')),
    conteudo      bytea        not null,
    largura       int          not null check (largura > 0),
    altura        int          not null check (altura > 0),
    dthr_criacao  timestamp    not null,
    criado_por    bigint       not null references usuario (id)
);
