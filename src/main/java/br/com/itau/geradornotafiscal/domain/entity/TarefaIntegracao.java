package br.com.itau.geradornotafiscal.domain.entity;

import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import br.com.itau.geradornotafiscal.domain.valueobject.StatusTarefa;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Acionamento de um sistema para a nota de um pedido (E02-RN-02). Cada mudança devolve uma tarefa nova com a versão
 * seguinte; quem grava confere a versão lida, para só um processo executar a tarefa (E02-NF-04).
 */
@Getter
public final class TarefaIntegracao {

    // Na 5ª falha a tarefa para de tentar (E02-NF-05).
    public static final int MAXIMO_DE_TENTATIVAS = 5;
    // Se quem pegou a tarefa cair, ela volta a poder ser pega depois disso (E02-NF-04).
    public static final Duration BLOQUEIO = Duration.ofSeconds(60);

    private final Long idPedido;
    private final Sistema sistema;
    private final StatusTarefa status;
    private final int tentativas;
    private final String ultimoErro;
    private final Instant bloqueadaAte;
    private final Instant pendenteDesde;
    private final long versao;

    private TarefaIntegracao(Long idPedido, Sistema sistema, StatusTarefa status, int tentativas, String ultimoErro,
                             Instant bloqueadaAte, Instant pendenteDesde, long versao) {
        this.idPedido = idPedido;
        this.sistema = sistema;
        this.status = status;
        this.tentativas = tentativas;
        this.ultimoErro = ultimoErro;
        this.bloqueadaAte = bloqueadaAte;
        this.pendenteDesde = pendenteDesde;
        this.versao = versao;
    }

    /** Tarefa criada junto com a nota, antes de qualquer acionamento (E02-RN-02). */
    public static TarefaIntegracao criarPendente(Long idPedido, Sistema sistema, Instant agora) {
        Objects.requireNonNull(idPedido, "idPedido obrigatório");
        Objects.requireNonNull(sistema, "sistema obrigatório");
        Objects.requireNonNull(agora, "agora obrigatório");
        return new TarefaIntegracao(idPedido, sistema, StatusTarefa.PENDENTE, 0, null, null, agora, 1);
    }

    /** Remonta a tarefa lida do armazenamento, sem aplicar regra de criação. */
    public static TarefaIntegracao reconstituir(Long idPedido, Sistema sistema, StatusTarefa status, int tentativas,
                                                String ultimoErro, Instant bloqueadaAte, Instant pendenteDesde, long versao) {
        return new TarefaIntegracao(idPedido, sistema, status, tentativas, ultimoErro, bloqueadaAte, pendenteDesde, versao);
    }

    public boolean terminada() {
        return status == StatusTarefa.CONCLUIDA || status == StatusTarefa.FALHOU;
    }

    /** Pendente, ou em execução por um processo que não terminou dentro do bloqueio (E02-NF-04). */
    public boolean podeSerPega(Instant agora) {
        return status == StatusTarefa.PENDENTE
                || status == StatusTarefa.EM_EXECUCAO && bloqueadaAte != null && !bloqueadaAte.isAfter(agora);
    }

    public TarefaIntegracao pegar(Instant agora) {
        if (!podeSerPega(agora)) {
            throw new IllegalStateException("Tarefa não pode ser pega no status " + status);
        }
        return new TarefaIntegracao(idPedido, sistema, StatusTarefa.EM_EXECUCAO, tentativas, ultimoErro,
                agora.plus(BLOQUEIO), pendenteDesde, versao + 1);
    }

    public TarefaIntegracao concluir() {
        exigirEmExecucao();
        return new TarefaIntegracao(idPedido, sistema, StatusTarefa.CONCLUIDA, tentativas + 1, ultimoErro, null, null,
                versao + 1);
    }

    /** Falha volta a pendente para nova tentativa; na 5ª, a tarefa fica como FALHOU (E02-RN-04, E02-RN-05). */
    public TarefaIntegracao falhar(String erro) {
        exigirEmExecucao();
        int tentativasFeitas = tentativas + 1;
        boolean esgotou = tentativasFeitas >= MAXIMO_DE_TENTATIVAS;
        return new TarefaIntegracao(idPedido, sistema, esgotou ? StatusTarefa.FALHOU : StatusTarefa.PENDENTE,
                tentativasFeitas, erro, null, esgotou ? null : pendenteDesde, versao + 1);
    }

    private void exigirEmExecucao() {
        if (status != StatusTarefa.EM_EXECUCAO) {
            throw new IllegalStateException("Tarefa não está em execução; status " + status);
        }
    }
}
