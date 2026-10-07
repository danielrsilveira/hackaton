-- O login (Cognito) é mapeado para o usuário pelo e-mail: ele precisa identificar uma única linha.
-- Índice parcial e case-insensitive, porque a coluna email é opcional.
create unique index ux_usuario_email on usuario (lower(email)) where email is not null;
