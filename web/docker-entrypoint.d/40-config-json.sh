#!/bin/sh
# Gera /usr/share/nginx/html/config.json ao iniciar o container, a partir de variáveis de ambiente.
# A configuração de autenticação do front vem daqui (runtime), nunca do build da imagem.
#
#   AUTH_MODO            simulado (padrão) | cognito
#   COGNITO_DOMAIN       https://<prefixo>.auth.us-east-1.amazoncognito.com   (modo cognito)
#   COGNITO_CLIENT_ID    id do app client público                              (modo cognito)
#   COGNITO_REDIRECT_URI opcional; padrão = origem do site (ex.: http://localhost:4200)
#   COGNITO_LOGOUT_URI   opcional; padrão = origem do site
set -eu

ALVO=/usr/share/nginx/html/config.json
MODO="${AUTH_MODO:-simulado}"

# Os valores entram literalmente no JSON: aceita só caracteres de URL/identificador (sem aspas,
# barra invertida, espaço nem quebra de linha). Recusa o start do container se houver algo fora disso.
seguro() {
  case "$2" in
    *[!A-Za-z0-9._:/@%=~+,-]*)
      echo "40-config-json: valor inválido em $1" >&2
      exit 1
      ;;
  esac
}

case "$MODO" in
  simulado)
    printf '{"authModo":"simulado"}\n' > "$ALVO"
    ;;
  cognito)
    DOMINIO="${COGNITO_DOMAIN:-}"
    CLIENTE="${COGNITO_CLIENT_ID:-}"
    if [ -z "$DOMINIO" ] || [ -z "$CLIENTE" ]; then
      echo "40-config-json: AUTH_MODO=cognito exige COGNITO_DOMAIN e COGNITO_CLIENT_ID" >&2
      exit 1
    fi
    seguro COGNITO_DOMAIN "$DOMINIO"
    seguro COGNITO_CLIENT_ID "$CLIENTE"
    EXTRA=""
    if [ -n "${COGNITO_REDIRECT_URI:-}" ]; then
      seguro COGNITO_REDIRECT_URI "$COGNITO_REDIRECT_URI"
      EXTRA="$EXTRA,\"redirectUri\":\"$COGNITO_REDIRECT_URI\""
    fi
    if [ -n "${COGNITO_LOGOUT_URI:-}" ]; then
      seguro COGNITO_LOGOUT_URI "$COGNITO_LOGOUT_URI"
      EXTRA="$EXTRA,\"logoutUri\":\"$COGNITO_LOGOUT_URI\""
    fi
    printf '{"authModo":"cognito","cognitoDomain":"%s","clientId":"%s"%s}\n' "$DOMINIO" "$CLIENTE" "$EXTRA" > "$ALVO"
    ;;
  *)
    echo "40-config-json: AUTH_MODO inválido (use simulado ou cognito)" >&2
    exit 1
    ;;
esac
echo "40-config-json: config.json gerado (modo $MODO)"
