package br.com.itau.geradornotafiscal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga as rotinas agendadas, como a reconciliação das tarefas (E02-NF-06). */
@Configuration
@EnableScheduling
public class AgendamentoConfig {
}
