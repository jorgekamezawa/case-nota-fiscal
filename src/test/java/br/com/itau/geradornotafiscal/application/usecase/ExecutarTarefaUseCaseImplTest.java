package br.com.itau.geradornotafiscal.application.usecase;

import br.com.itau.geradornotafiscal.application.port.in.result.ResultadoTarefa;
import br.com.itau.geradornotafiscal.application.port.in.result.TarefaExecutada;
import br.com.itau.geradornotafiscal.application.port.out.EntregaPort;
import br.com.itau.geradornotafiscal.application.port.out.EstoquePort;
import br.com.itau.geradornotafiscal.application.port.out.FinanceiroPort;
import br.com.itau.geradornotafiscal.application.port.out.NotaFiscalPersistenciaPort;
import br.com.itau.geradornotafiscal.application.port.out.NotaFiscalPersistenciaPort.NotaGuardada;
import br.com.itau.geradornotafiscal.application.port.out.RegistroPort;
import br.com.itau.geradornotafiscal.application.port.out.TarefaIntegracaoPort;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import br.com.itau.geradornotafiscal.domain.entity.TarefaIntegracao;
import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import br.com.itau.geradornotafiscal.domain.valueobject.StatusTarefa;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoPessoa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutarTarefaUseCaseImplTest {

    private static final Instant AGORA = Instant.parse("2026-01-15T15:30:00Z");
    private static final long ID_PEDIDO = 123L;
    private static final NotaFiscal NOTA = NotaFiscal.reconstituir("7d1c5b1e-2f4a-4c1b-9a53-0c7c4e9b2a10",
            LocalDateTime.of(2026, 1, 15, 12, 30), new BigDecimal("100.00"), new BigDecimal("10.48"), List.of(),
            new Destinatario("Fulano de Tal", TipoPessoa.FISICA, null, List.of(), List.of()));

    @Mock
    private TarefaIntegracaoPort tarefaIntegracaoPort;
    @Mock
    private NotaFiscalPersistenciaPort notaFiscalPersistenciaPort;
    @Mock
    private RegistroPort registroPort;
    @Mock
    private EstoquePort estoquePort;
    @Mock
    private EntregaPort entregaPort;
    @Mock
    private FinanceiroPort financeiroPort;

    private ExecutarTarefaUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ExecutarTarefaUseCaseImpl(Clock.fixed(AGORA, ZoneOffset.UTC), tarefaIntegracaoPort,
                notaFiscalPersistenciaPort, registroPort, estoquePort, entregaPort, financeiroPort);
    }

    @Test
    @DisplayName("E02-RN-03: tarefa pendente aciona o sistema com a nota guardada, que leva o identificador da nota, e termina")
    void e02Rn03_acionaComANotaEConclui() {
        tarefaGuardada(pendente(Sistema.ENTREGA));
        salvarVence();
        when(notaFiscalPersistenciaPort.buscar(ID_PEDIDO)).thenReturn(Optional.of(new NotaGuardada(NOTA, "hash")));

        TarefaExecutada executada = useCase.executar(ID_PEDIDO, Sistema.ENTREGA);

        assertEquals(ResultadoTarefa.CONCLUIDA, executada.resultado());
        assertEquals(AGORA.minusSeconds(120), executada.pendenteDesde());
        verify(entregaPort).agendarEntrega(NOTA);
        verifyNoInteractions(registroPort, estoquePort, financeiroPort);
        assertEquals(StatusTarefa.CONCLUIDA, ultimaGravada().getStatus());
    }

    @Test
    @DisplayName("E02-RN-04: falha volta a tarefa a pendente e mantém a mensagem para nova tentativa, só com o tipo do erro")
    void e02Rn04_falhaTentaDeNovo() {
        tarefaGuardada(pendente(Sistema.ESTOQUE));
        salvarVence();
        when(notaFiscalPersistenciaPort.buscar(ID_PEDIDO)).thenReturn(Optional.of(new NotaGuardada(NOTA, "hash")));
        doThrow(new IllegalStateException("destinatário Fulano de Tal")).when(estoquePort).enviarNotaFiscalParaBaixaEstoque(NOTA);

        assertEquals(ResultadoTarefa.NOVA_TENTATIVA, useCase.executar(ID_PEDIDO, Sistema.ESTOQUE).resultado());

        TarefaIntegracao gravada = ultimaGravada();
        assertEquals(StatusTarefa.PENDENTE, gravada.getStatus());
        assertEquals(1, gravada.getTentativas());
        assertEquals("IllegalStateException", gravada.getUltimoErro());
    }

    @Test
    @DisplayName("E02-RN-05, E02-NF-05: a 5ª falha deixa a tarefa como FALHOU e move a mensagem para a DLQ")
    void e02Rn05_quintaFalhaVaiParaDlq() {
        TarefaIntegracao quatroFalhas = pendente(Sistema.FINANCEIRO);
        for (int i = 0; i < 4; i++) {
            quatroFalhas = quatroFalhas.pegar(AGORA).falhar("IllegalStateException");
        }
        tarefaGuardada(quatroFalhas);
        salvarVence();
        when(notaFiscalPersistenciaPort.buscar(ID_PEDIDO)).thenReturn(Optional.of(new NotaGuardada(NOTA, "hash")));
        doThrow(new IllegalStateException()).when(financeiroPort).enviarNotaFiscalParaContasReceber(NOTA);

        assertEquals(ResultadoTarefa.FALHOU, useCase.executar(ID_PEDIDO, Sistema.FINANCEIRO).resultado());
        assertEquals(StatusTarefa.FALHOU, ultimaGravada().getStatus());
    }

    @Test
    @DisplayName("E02-NF-04: mensagem repetida de tarefa terminada sai da fila sem acionar o sistema")
    void e02Nf04_tarefaTerminada() {
        tarefaGuardada(pendente(Sistema.REGISTRO).pegar(AGORA).concluir());

        assertEquals(ResultadoTarefa.JA_TERMINADA, useCase.executar(ID_PEDIDO, Sistema.REGISTRO).resultado());

        verifyNoInteractions(registroPort);
    }

    @Test
    @DisplayName("E02-NF-04: tarefa em execução por outro processo, dentro do bloqueio: a mensagem fica e o sistema não é acionado")
    void e02Nf04_emExecucaoPorOutro() {
        tarefaGuardada(pendente(Sistema.REGISTRO).pegar(AGORA.minusSeconds(10)));

        assertEquals(ResultadoTarefa.EM_EXECUCAO_POR_OUTRO, useCase.executar(ID_PEDIDO, Sistema.REGISTRO).resultado());

        verifyNoInteractions(registroPort);
    }

    @Test
    @DisplayName("E02-NF-04: dois processos com a mesma mensagem; quem perde a gravação condicional não aciona o sistema")
    void e02Nf04_perdeuADisputa() {
        tarefaGuardada(pendente(Sistema.REGISTRO));
        when(tarefaIntegracaoPort.salvar(any(), anyLong())).thenReturn(false);

        assertEquals(ResultadoTarefa.EM_EXECUCAO_POR_OUTRO, useCase.executar(ID_PEDIDO, Sistema.REGISTRO).resultado());

        verifyNoInteractions(registroPort);
    }

    @Test
    @DisplayName("E02-NF-10: processo que caiu com a tarefa em execução; vencido o bloqueio, outro processo a executa")
    void e02Nf10_bloqueioVencido() {
        tarefaGuardada(pendente(Sistema.ENTREGA).pegar(AGORA.minusSeconds(61)));
        salvarVence();
        when(notaFiscalPersistenciaPort.buscar(ID_PEDIDO)).thenReturn(Optional.of(new NotaGuardada(NOTA, "hash")));

        assertEquals(ResultadoTarefa.CONCLUIDA, useCase.executar(ID_PEDIDO, Sistema.ENTREGA).resultado());

        verify(entregaPort).agendarEntrega(NOTA);
    }

    private static TarefaIntegracao pendente(Sistema sistema) {
        return TarefaIntegracao.criarPendente(ID_PEDIDO, sistema, AGORA.minusSeconds(120));
    }

    private void tarefaGuardada(TarefaIntegracao tarefa) {
        when(tarefaIntegracaoPort.buscar(ID_PEDIDO, tarefa.getSistema())).thenReturn(Optional.of(tarefa));
    }

    private void salvarVence() {
        when(tarefaIntegracaoPort.salvar(any(), anyLong())).thenReturn(true);
    }

    private TarefaIntegracao ultimaGravada() {
        ArgumentCaptor<TarefaIntegracao> gravadas = ArgumentCaptor.forClass(TarefaIntegracao.class);
        verify(tarefaIntegracaoPort, org.mockito.Mockito.atLeastOnce()).salvar(gravadas.capture(), anyLong());
        return gravadas.getAllValues().getLast();
    }
}
