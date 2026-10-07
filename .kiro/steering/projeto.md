# SISGARES – convenções do projeto

Sistema de reservas de ambientes, recursos e serviços (hackathon MPF). Especificação em `docs/`.
A equipe usa Windows, macOS e Linux: todo comando precisa funcionar em PowerShell, cmd e bash.
Regra de ouro: `docker compose up` (sem flags, sem `.env`) deve sempre subir o sistema completo em http://localhost:4200.

## Stack e estrutura
- `api/`: Java 21, Spring Boot 4.1, Maven Wrapper, JPA, Flyway, PostgreSQL 17. Pacote base `br.mp.mpf.sisgares`.
- `web/`: Angular 22 (standalone, signals, zoneless), CSS próprio, testes com Vitest. Imagem: build Node + nginx (`web/nginx.conf` faz o proxy de `/api`).
- `docker-compose.yml` (projeto `sisgares`): `db`, `api` e `web` sobem por padrão; `web-dev` (profile `dev`) é o front com recarga em container.
- Imagens base multi-arquitetura (amd64/arm64). Não usar bind mounts nos serviços padrão (lentos no Windows/macOS).
- Seeds CSV em `api/src/main/resources/seed/` (UTF-8). Nunca alterar `docs/`.

## Comandos
- Tudo: `docker compose up` (ou `-d`); banco limpo: `docker compose down -v`
- Só banco: `docker compose up -d db` (Postgres em localhost:5433)
- Banco + API: `docker compose up -d db api`
- API nativa: `./mvnw spring-boot:run` (Linux) ou `.\mvnw.cmd spring-boot:run` (Windows), dentro de `api/`
- Testes API: `./mvnw test` ou `.\mvnw.cmd test` (requer o banco de pé)
- Web nativa: `npm ci` e `npm start`, dentro de `web/`; testes com `npm test`
- Não instalar Maven nem Angular CLI globalmente; usar `mvnw` e os scripts npm.

## Backend
- Configuração só por variáveis de ambiente com defaults locais em `application.yml`.
- Datas: `LocalDateTime` sem fuso (fuso da JVM: America/Fortaleza). O front envia `yyyy-MM-ddTHH:mm`.
- REST sob `/api`. Erros de validação: HTTP 422 com corpo `[{"regra":"RN5","mensagem":"..."}]`.
- Regras de negócio em classes puras e testáveis, com `java.time.Clock` injetado.
- Spring Boot 4 usa Jackson 3 (pacote `tools.jackson`, não `com.fasterxml.jackson`) e starters modulares (ex.: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`).
- Camadas: `dominio/` (regras puras, sem Spring), `infra/` (JdbcClient, seed, SNP, e-mail), `servico/`, `web/` (controllers).
  Acesso a dados com `JdbcClient` e SQL explícito (sem entidades JPA). Toda regra nova ganha teste em `RegrasNegocioTest`.
- Usuário simulado pelo header `X-Usuario-Id` (resolvido por `UsuarioAtual`); checar perfil no backend, nunca só no front.
- Schema somente via Flyway (`ddl-auto: validate`). Nome das migrations: `V<AAAAMMDDHHmm>__<descricao>.sql`
  (ex.: `V202610071530__cria_reserva.sql`). `V1__init.sql` é reservado ao modelo base. Nunca editar uma migration já mergeada.
- E-mail e SNP são simulados atrás de interfaces (trocáveis por SES / API real depois).

## Frontend
- Chamadas de API sempre relativas (`/api/...`); o proxy (`proxy.conf.js` no `npm start`, `nginx.conf` no container e, na AWS, o CloudFront) faz o roteamento.
- Visual: usar os tokens de `src/styles.css` (`--primaria`, `--suave`, `--raio`, `.cartao`, `.cabecalho-pagina`, `.status`, botões
  `.secundario`/`.fantasma`/`.perigo`) e ícones via `<app-icone nome="..." />` (`src/app/icone.ts`). Não adicionar bibliotecas de UI nem fontes externas.
- Acessibilidade (eMAG/WCAG): `<label>` em todo campo, navegação por teclado, foco visível, contraste ≥ 4.5:1,
  estado nunca indicado só por cor, `alt` em imagens. Layout responsivo (desktop e celular).

## Dados e segurança
- Apenas dados fictícios (LGPD). Dados pessoais só são exibidos a quem precisa deles (dono da reserva, administrador, atendente).
- Nunca versionar credenciais nem `.env`.

## AWS
- Todo comando AWS CLI, SAM ou CloudFormation usa `--profile workshop` e a região `us-east-1`; código SDK usa o profile `workshop`.
- O deploy será via CloudFormation (ainda não implementado). Não criar recursos na conta sem combinar com a equipe.

## Git
- Branch por feature (`feat/<area>-<descricao>`), PR para `main`, sem commits diretos na `main`.
