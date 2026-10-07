package br.mp.mpf.sisgares.infra.email;

/** Envio de e-mail. Na demonstração só registra em log; na AWS pode ser trocado por SES. */
public interface EmailSender {

    void enviar(String destinatarios, String assunto, String html);
}
