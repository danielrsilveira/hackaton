package br.mp.mpf.sisgares;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SisgaresApiApplication {

	/** Fuso único da aplicação: todas as datas são LocalDateTime nesse fuso. */
	public static final String TIME_ZONE = "America/Fortaleza";

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(TIME_ZONE));
		SpringApplication.run(SisgaresApiApplication.class, args);
	}
}
