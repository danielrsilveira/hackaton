package br.mp.mpf.sisgares.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import br.mp.mpf.sisgares.dominio.Erro;
import br.mp.mpf.sisgares.dominio.RegraException;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** Violação de regras de negócio: 422 com [{regra, mensagem}]. */
    @ExceptionHandler(RegraException.class)
    public ResponseEntity<List<Erro>> regra(RegraException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(e.getErros());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<List<Erro>> arquivoGrande(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(List.of(new Erro("RF06", "O ícone deve ter até 100 KB.")));
    }

    @ExceptionHandler({ MissingServletRequestPartException.class, MultipartException.class })
    public ResponseEntity<List<Erro>> semArquivo(Exception e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(List.of(new Erro("RF06", "Escolha um arquivo de imagem.")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<List<Erro>> corpoInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(List.of(new Erro("FORMATO", "Dados em formato inválido (datas: yyyy-MM-ddTHH:mm).")));
    }
}
