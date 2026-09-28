package br.com.itau.geradornotafiscal.adapter.out.dynamodb;

import br.com.itau.geradornotafiscal.PedidoBase;
import br.com.itau.geradornotafiscal.application.exception.NotaJaGuardadaException;
import br.com.itau.geradornotafiscal.domain.entity.TarefaIntegracao;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import br.com.itau.geradornotafiscal.domain.valueobject.StatusTarefa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tarefas de integração no emulador oficial do DynamoDB (E02-NF-02).
 */
@SpringBootTest
class TarefaIntegracaoDynamoAdapterTest {

    private static final LocalDate APAGAR_EM = LocalDate.of(2032, 1, 1);

    @Autowired
    private NotaFiscalDynamoAdapter notas;
    @Autowired
    private TarefaIntegracaoDynamoAdapter tarefas;

    @Test
    @DisplayName("E02-RN-02, E02-NF-02: a nota e as quatro tarefas pendentes são gravadas juntas")
    void e02Rn02_notaEQuatroTarefas() {
        long idPedido = PedidoBase.novoId();
        Instant emissao = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        notas.guardar(idPedido, NotaFiscalDynamoAdapterTest.nota(1, "Teclado USB"), "a".repeat(64), APAGAR_EM,
                pendentes(idPedido, emissao));

        for (Sistema sistema : Sistema.values()) {
            TarefaIntegracao tarefa = tarefas.buscar(idPedido, sistema).orElseThrow();
            assertEquals(StatusTarefa.PENDENTE, tarefa.getStatus());
            assertEquals(emissao, tarefa.getPendenteDesde());
        }
    }

    @Test
    @DisplayName("E02-RN-02: se a nota não é gravada, as tarefas também não são")
    void e02Rn02_nadaGravadoSemANota() {
        long idPedido = PedidoBase.novoId();
        Instant primeira = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MILLIS);
        notas.guardar(idPedido, NotaFiscalDynamoAdapterTest.nota(1, "Teclado USB"), "a".repeat(64), APAGAR_EM,
                pendentes(idPedido, primeira));

        assertThatThrownBy(() -> notas.guardar(idPedido, NotaFiscalDynamoAdapterTest.nota(1, "Outro"), "b".repeat(64),
                APAGAR_EM, pendentes(idPedido, Instant.now()))).isInstanceOf(NotaJaGuardadaException.class);

        assertEquals(primeira, tarefas.buscar(idPedido, Sistema.ENTREGA).orElseThrow().getPendenteDesde());
    }

    @Test
    @DisplayName("E02-NF-04: a gravação só vale para quem leu a versão atual")
    void e02Nf04_gravacaoCondicionalPelaVersao() {
        long idPedido = PedidoBase.novoId();
        notas.guardar(idPedido, NotaFiscalDynamoAdapterTest.nota(1, "Teclado USB"), "a".repeat(64), APAGAR_EM,
                pendentes(idPedido, Instant.now()));
        TarefaIntegracao lida = tarefas.buscar(idPedido, Sistema.REGISTRO).orElseThrow();

        assertTrue(tarefas.salvar(lida.pegar(Instant.now()), lida.getVersao()));
        assertFalse(tarefas.salvar(lida.pegar(Instant.now()), lida.getVersao()), "segundo processo com a mesma versão");

        assertEquals(StatusTarefa.EM_EXECUCAO, tarefas.buscar(idPedido, Sistema.REGISTRO).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("E02-NF-02, E02-NF-06: o índice só traz as tarefas abertas antes do limite; a concluída sai dele")
    void e02Nf06_abertasPeloIndice() {
        long idPedido = PedidoBase.novoId();
        Instant antiga = Instant.now().minusSeconds(3600).truncatedTo(ChronoUnit.MILLIS);
        notas.guardar(idPedido, NotaFiscalDynamoAdapterTest.nota(1, "Teclado USB"), "a".repeat(64), APAGAR_EM,
                pendentes(idPedido, antiga));
        TarefaIntegracao estoque = tarefas.buscar(idPedido, Sistema.ESTOQUE).orElseThrow();
        TarefaIntegracao emExecucao = estoque.pegar(Instant.now());
        tarefas.salvar(emExecucao, estoque.getVersao());
        tarefas.salvar(emExecucao.concluir(), emExecucao.getVersao());

        List<Sistema> abertas = tarefas.abertasDesdeAntesDe(Instant.now().minusSeconds(60)).stream()
                .filter(tarefa -> tarefa.getIdPedido() == idPedido).map(TarefaIntegracao::getSistema).toList();

        assertThat(abertas).containsExactlyInAnyOrder(Sistema.REGISTRO, Sistema.ENTREGA, Sistema.FINANCEIRO);
        assertThat(tarefas.abertasDesdeAntesDe(antiga)).noneMatch(tarefa -> tarefa.getIdPedido() == idPedido);
    }

    static List<TarefaIntegracao> pendentes(long idPedido, Instant desde) {
        return Arrays.stream(Sistema.values()).map(sistema -> TarefaIntegracao.criarPendente(idPedido, sistema, desde)).toList();
    }
}
