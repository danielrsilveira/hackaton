package br.mp.mpf.sisgares.dominio;

import java.util.List;

/**
 * Ícones disponíveis para recursos (RF06: "uma imagem na forma de ícone dentre as disponíveis").
 * Os do sistema ficam em {@code web/public/img/recurso}; ao incluir um arquivo lá, inclua-o também aqui.
 * Os enviados pelo administrador ficam no banco, com nome {@code up-<id>} (sem extensão, para a URL
 * {@code /api/icones-recurso/up-<id>} não cair na regra de arquivos estáticos do nginx/CloudFront).
 */
public final class IconesRecurso {

    public static final String PREFIXO_ENVIADO = "up-";

    public static String nomeEnviado(long id) {
        return PREFIXO_ENVIADO + id;
    }

    public static final List<String> DISPONIVEIS = List.of(
            "serv_001.png", "serv_002.png", "serv_003.png",
            "estrut_001.png", "estrut_002.png",
            "equip_001.png", "equip_002.png", "equip_003.png", "equip_004.png", "equip_005.png", "equip_006.png",
            "equip_007.png", "equip_008.png", "equip_009.png", "equip_010.png", "equip_011.png",
            "indefinido.png");

    private IconesRecurso() {
    }
}
