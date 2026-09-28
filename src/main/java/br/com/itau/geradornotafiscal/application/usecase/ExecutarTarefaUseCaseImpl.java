package br.com.itau.geradornotafiscal.application.usecase;

import br.com.itau.geradornotafiscal.application.port.in.ExecutarTarefaUseCase;
import br.com.itau.geradornotafiscal.application.port.in.ResultadoTarefa;
import br.com.itau.geradornotafiscal.application.port.in.TarefaExecutada;
import br.com.itau.geradornotafiscal.application.port.out.EntregaPort;
import br.com.itau.geradornotafiscal.application.port.out.EstoquePort;
import br.com.itau.geradornotafiscal.application.port.out.FinanceiroPort;
import br.com.itau.geradornotafiscal.application.port.out.NotaFiscalPersistenciaPort;
import br.com.itau.geradornotafiscal.application.port.out.NotaFiscalPersistenciaPort.NotaGuardada;
import br.com.itau.geradornotafiscal.application.port.out.RegistroPort;
import br.com.itau.geradornotafiscal.application.port.out.TarefaIntegracaoPort;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import br.com.itau.geradornotafiscal.domain.entity.TarefaIntegracao;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExecutarTarefaUseCaseImpl implements ExecutarTarefaUseCase {

    private final Clock relogio;
    private final TarefaIntegracaoPort tarefaIntegracaoPort;
    private final NotaFiscalPersistenciaPort notaFiscalPersistenciaPort;
    private final RegistroPort registroPort;
    private final EstoquePort estoquePort;
    private final EntregaPort entregaPort;
    private final FinanceiroPort financeiroPort;

    @Override
    public TarefaExecutada executar(Long idPedido, Sistema sistema) {
        Optional<TarefaIntegracao> lida = tarefaIntegracaoPort.buscar(idPedido, sistema);
        // Tarefa terminada ou que não existe mais (expurgo): a mensagem repetida sai da fila.
        if (lida.isEmpty() || lida.get().terminada()) {
            return new TarefaExecutada(ResultadoTarefa.JA_TERMINADA, null);
        }
        TarefaIntegracao tarefa = lida.get();
        // Outro processo está com a tarefa: a mensagem fica para a nova tentativa dele (E02-NF-04).
        if (!tarefa.podeSerPega(relogio.instant())) {
            return new TarefaExecutada(ResultadoTarefa.EM_EXECUCAO_POR_OUTRO, tarefa.getPendenteDesde());
        }
        TarefaIntegracao emExecucao = tarefa.pegar(relogio.instant());
        if (!tarefaIntegracaoPort.salvar(emExecucao, tarefa.getVersao())) {
            return new TarefaExecutada(ResultadoTarefa.EM_EXECUCAO_POR_OUTRO, tarefa.getPendenteDesde());
        }

        TarefaIntegracao depois;
        try {
            acionar(sistema, notaDoPedido(idPedido));
            depois = emExecucao.concluir();
        } catch (RuntimeException e) {
            // Só o tipo do erro: a mensagem pode trazer dado do pedido.
            depois = emExecucao.falhar(e.getClass().getSimpleName());
        }
        // Se o bloqueio venceu e outro processo pegou a tarefa, ele termina: a mensagem fica.
        if (!tarefaIntegracaoPort.salvar(depois, emExecucao.getVersao())) {
            return new TarefaExecutada(ResultadoTarefa.EM_EXECUCAO_POR_OUTRO, tarefa.getPendenteDesde());
        }
        ResultadoTarefa resultado = switch (depois.getStatus()) {
            case CONCLUIDA -> ResultadoTarefa.CONCLUIDA;
            case FALHOU -> ResultadoTarefa.FALHOU;
            case PENDENTE, EM_EXECUCAO -> ResultadoTarefa.NOVA_TENTATIVA;
        };
        return new TarefaExecutada(resultado, tarefa.getPendenteDesde());
    }

    private NotaFiscal notaDoPedido(Long idPedido) {
        return notaFiscalPersistenciaPort.buscar(idPedido).map(NotaGuardada::nota)
                .orElseThrow(() -> new IllegalStateException("Tarefa sem nota guardada"));
    }

    // A nota leva o identificador que o sistema usa para descartar a repetição (E02-RN-03).
    private void acionar(Sistema sistema, NotaFiscal nota) {
        switch (sistema) {
            case REGISTRO -> registroPort.registrarNotaFiscal(nota);
            case ESTOQUE -> estoquePort.enviarNotaFiscalParaBaixaEstoque(nota);
            case ENTREGA -> entregaPort.agendarEntrega(nota);
            case FINANCEIRO -> financeiroPort.enviarNotaFiscalParaContasReceber(nota);
        }
    }
}
