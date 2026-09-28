package br.com.itau.geradornotafiscal.application.port.in;

import java.time.Instant;

/**
 * Resultado da tarefa e desde quando ela estava aberta, para medir a conclusão em até 5 minutos (RFC 6.4).
 */
public record TarefaExecutada(ResultadoTarefa resultado, Instant pendenteDesde) {
}
