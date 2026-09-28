package br.com.itau.geradornotafiscal.adapter.out.dynamodb.mappers;

import br.com.itau.geradornotafiscal.adapter.out.dynamodb.dto.TarefaIntegracaoRegistro;
import br.com.itau.geradornotafiscal.domain.entity.TarefaIntegracao;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import br.com.itau.geradornotafiscal.domain.valueobject.StatusTarefa;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Domínio para o formato guardado e de volta.
 */
@Component
public class TarefaIntegracaoRegistroMapper {

    // Espalha as tarefas abertas por 10 partições do índice, para não concentrar gravações numa só (Spike-0001).
    public static final int FATIAS = 10;

    public TarefaIntegracaoRegistro paraRegistro(TarefaIntegracao tarefa) {
        boolean aberta = tarefa.getPendenteDesde() != null;
        return new TarefaIntegracaoRegistro(
                tarefa.getIdPedido(),
                tarefa.getSistema().name(),
                tarefa.getStatus().name(),
                tarefa.getTentativas(),
                tarefa.getUltimoErro(),
                milissegundos(tarefa.getBloqueadaAte()),
                aberta ? fatia(tarefa.getIdPedido()) : null,
                milissegundos(tarefa.getPendenteDesde()),
                tarefa.getVersao());
    }

    public TarefaIntegracao paraDominio(TarefaIntegracaoRegistro registro) {
        return TarefaIntegracao.reconstituir(
                registro.idPedido(),
                Sistema.valueOf(registro.sistema()),
                StatusTarefa.valueOf(registro.status()),
                registro.tentativas(),
                registro.ultimoErro(),
                instante(registro.bloqueadaAte()),
                instante(registro.pendenteDesde()),
                registro.versao());
    }

    public static String fatia(long idPedido) {
        return "PENDENTE#" + Math.floorMod(idPedido, FATIAS);
    }

    public static String fatia(int numero) {
        return "PENDENTE#" + numero;
    }

    private static Long milissegundos(Instant instante) {
        return instante == null ? null : instante.toEpochMilli();
    }

    private static Instant instante(Long milissegundos) {
        return milissegundos == null ? null : Instant.ofEpochMilli(milissegundos);
    }
}
