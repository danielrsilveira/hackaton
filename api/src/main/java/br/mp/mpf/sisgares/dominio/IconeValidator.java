package br.mp.mpf.sisgares.dominio;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Regras do envio de ícone de recurso (RF06). Classe pura (só JDK).
 * <ul>
 * <li>PNG, JPEG ou GIF, identificados pelo conteúdo (não pela extensão nem pelo tipo informado).
 * SVG não é aceito: pode carregar script.</li>
 * <li>Até 100 KB, decodificável como imagem, de 16 a 512 pixels em cada lado.</li>
 * </ul>
 */
public final class IconeValidator {

    public static final int TAMANHO_MAXIMO = 100 * 1024;
    public static final int LADO_MINIMO = 16;
    public static final int LADO_MAXIMO = 512;

    /** Ícone aceito, com o tipo detectado pelo conteúdo. */
    public record Icone(String tipo, int largura, int altura) {
    }

    /** Resultado: ícone aceito ou os erros encontrados. */
    public record Resultado(Icone icone, List<Erro> erros) {
        public boolean valido() {
            return icone != null;
        }
    }

    private IconeValidator() {
    }

    public static Resultado validar(byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0) {
            return erro("Escolha um arquivo de imagem.");
        }
        if (conteudo.length > TAMANHO_MAXIMO) {
            return erro("O ícone deve ter até %d KB.".formatted(TAMANHO_MAXIMO / 1024));
        }
        String tipo = tipo(conteudo);
        if (tipo == null) {
            return erro("Envie uma imagem PNG, JPEG ou GIF.");
        }
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(conteudo));
        } catch (IOException | RuntimeException e) {
            img = null;
        }
        if (img == null) {
            return erro("Não foi possível ler a imagem enviada.");
        }
        int w = img.getWidth();
        int h = img.getHeight();
        if (w < LADO_MINIMO || h < LADO_MINIMO || w > LADO_MAXIMO || h > LADO_MAXIMO) {
            return erro("O ícone deve ter de %d a %d pixels em cada lado (a imagem tem %d×%d)."
                    .formatted(LADO_MINIMO, LADO_MAXIMO, w, h));
        }
        return new Resultado(new Icone(tipo, w, h), List.of());
    }

    /** Tipo pela assinatura dos primeiros bytes; null se não for PNG, JPEG nem GIF. */
    static String tipo(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return "image/gif";
        }
        return null;
    }

    private static Resultado erro(String mensagem) {
        return new Resultado(null, List.of(new Erro(RecursoValidator.REGRA, mensagem)));
    }
}
