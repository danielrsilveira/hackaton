package br.mp.mpf.sisgares.infra.seguranca;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Emissor de ID tokens falsos no formato do Cognito, assinados com uma chave RSA gerada no teste.
 * O {@link #decoder()} usa os mesmos validadores de produção ({@link ValidadoresCognito}); só a origem da
 * chave pública muda (local em vez do JWKS do user pool).
 */
public final class TokenDeTeste {

    public static final String ISSUER = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_TESTE0001";
    public static final String CLIENT_ID = "client-id-de-teste";

    private final KeyPair chaves = gerarChaves();

    public JwtDecoder decoder() {
        NimbusJwtDecoder d = NimbusJwtDecoder.withPublicKey((RSAPublicKey) chaves.getPublic()).build();
        d.setJwtValidator(ValidadoresCognito.para(ISSUER, CLIENT_ID));
        return d;
    }

    /** ID token válido do e-mail informado (e-mail verificado, expira em 1 h). */
    public String idToken(String email) {
        return emitir(base(email), chaves);
    }

    public Builder novo(String email) {
        return new Builder(email);
    }

    /** Variações de claims para os cenários negativos. */
    public final class Builder {
        private String issuer = ISSUER;
        private String audience = CLIENT_ID;
        private String tokenUse = "id";
        private Object emailVerified = true;
        private Instant expira = Instant.now().plusSeconds(3600);
        private final String email;
        private KeyPair assinatura = chaves;

        private Builder(String email) {
            this.email = email;
        }

        public Builder issuer(String v) { issuer = v; return this; }
        public Builder audience(String v) { audience = v; return this; }
        public Builder tokenUse(String v) { tokenUse = v; return this; }
        public Builder emailVerificado(Object v) { emailVerified = v; return this; }
        public Builder expira(Instant v) { expira = v; return this; }
        public Builder assinadoPorOutraChave() { assinatura = gerarChaves(); return this; }

        public String build() {
            JWTClaimsSet.Builder c = new JWTClaimsSet.Builder()
                    .issuer(issuer).audience(audience).subject("00000000-0000-0000-0000-000000000001")
                    .issueTime(Date.from(Instant.now())).expirationTime(Date.from(expira))
                    .claim("token_use", tokenUse);
            if (email != null) {
                c.claim("email", email);
            }
            if (emailVerified != null) {
                c.claim("email_verified", emailVerified);
            }
            return assinar(c.build(), assinatura);
        }
    }

    private JWTClaimsSet base(String email) {
        return new JWTClaimsSet.Builder().issuer(ISSUER).audience(CLIENT_ID)
                .subject("00000000-0000-0000-0000-000000000001").issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600))).claim("token_use", "id")
                .claim("email", email).claim("email_verified", true).build();
    }

    private static String emitir(JWTClaimsSet claims, KeyPair chaves) {
        return assinar(claims, chaves);
    }

    private static String assinar(JWTClaimsSet claims, KeyPair chaves) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
            jwt.sign(new RSASSASigner(chaves.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair gerarChaves() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
