package br.mp.mpf.sisgares.infra.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** E-mail simulado: o conteúdo completo fica na tabela notificacao (tela "Notificações"). */
@Component
public class LogEmailSender implements EmailSender {

    private static final Logger LOG = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public void enviar(String destinatarios, String assunto, String html) {
        LOG.info("E-mail simulado para [{}]: {}", destinatarios, assunto);
    }
}
