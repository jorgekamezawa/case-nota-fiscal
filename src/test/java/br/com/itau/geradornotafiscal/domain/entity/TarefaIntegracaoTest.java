package br.com.itau.geradornotafiscal.domain.entity;

import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import br.com.itau.geradornotafiscal.domain.valueobject.StatusTarefa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TarefaIntegracaoTest {

    private static final Instant AGORA = Instant.parse("2026-01-15T15:30:00Z");

    @Test
    @DisplayName("E02-RN-02: a tarefa nasce pendente, sem tentativas, aberta desde a emissão")
    void e02Rn02_nascePendente() {
        TarefaIntegracao tarefa = TarefaIntegracao.criarPendente(1L, Sistema.ENTREGA, AGORA);

        assertEquals(StatusTarefa.PENDENTE, tarefa.getStatus());
        assertEquals(0, tarefa.getTentativas());
        assertEquals(AGORA, tarefa.getPendenteDesde());
        assertTrue(tarefa.podeSerPega(AGORA));
    }

    @Test
    @DisplayName("E02-NF-04: em execução, só outro processo pega depois de vencido o bloqueio de 60 s")
    void e02Nf04_bloqueio() {
        TarefaIntegracao emExecucao = TarefaIntegracao.criarPendente(1L, Sistema.ENTREGA, AGORA).pegar(AGORA);

        assertEquals(StatusTarefa.EM_EXECUCAO, emExecucao.getStatus());
        assertFalse(emExecucao.podeSerPega(AGORA.plusSeconds(59)));
        assertTrue(emExecucao.podeSerPega(AGORA.plus(Duration.ofSeconds(60))));
        assertEquals(2, emExecucao.getVersao());
    }

    @Test
    @DisplayName("E02-RN-04, E02-RN-05, E02-NF-05: falha volta a pendente; na 5ª a tarefa fica como FALHOU e sai das abertas")
    void e02Rn05_quintaFalha() {
        TarefaIntegracao tarefa = TarefaIntegracao.criarPendente(1L, Sistema.ENTREGA, AGORA);
        for (int falha = 1; falha < TarefaIntegracao.MAXIMO_DE_TENTATIVAS; falha++) {
            tarefa = tarefa.pegar(AGORA).falhar("IllegalStateException");
            assertEquals(StatusTarefa.PENDENTE, tarefa.getStatus());
            assertEquals(falha, tarefa.getTentativas());
            assertEquals(AGORA, tarefa.getPendenteDesde());
        }

        TarefaIntegracao falhou = tarefa.pegar(AGORA).falhar("IllegalStateException");

        assertEquals(StatusTarefa.FALHOU, falhou.getStatus());
        assertEquals(5, falhou.getTentativas());
        assertEquals("IllegalStateException", falhou.getUltimoErro());
        assertNull(falhou.getPendenteDesde());
        assertTrue(falhou.terminada());
    }

    @Test
    @DisplayName("E02-NF-04: concluída sai das abertas e não é pega de novo")
    void e02Nf04_concluida() {
        TarefaIntegracao concluida = TarefaIntegracao.criarPendente(1L, Sistema.ESTOQUE, AGORA).pegar(AGORA).concluir();

        assertEquals(StatusTarefa.CONCLUIDA, concluida.getStatus());
        assertNull(concluida.getPendenteDesde());
        assertTrue(concluida.terminada());
        assertFalse(concluida.podeSerPega(AGORA.plusSeconds(3600)));
        assertThrows(IllegalStateException.class, () -> concluida.pegar(AGORA));
    }
}
