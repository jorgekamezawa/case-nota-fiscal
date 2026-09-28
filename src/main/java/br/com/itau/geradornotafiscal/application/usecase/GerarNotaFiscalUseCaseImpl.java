package br.com.itau.geradornotafiscal.application.usecase;

import br.com.itau.geradornotafiscal.application.exception.ArmazenamentoIndisponivelException;
import br.com.itau.geradornotafiscal.application.exception.ConflitoDeGravacaoException;
import br.com.itau.geradornotafiscal.application.exception.NotaJaGuardadaException;
import br.com.itau.geradornotafiscal.application.port.in.GerarNotaFiscalUseCase;
import br.com.itau.geradornotafiscal.application.port.in.ReenvioUseCase;
import br.com.itau.geradornotafiscal.application.port.in.ResultadoDaEmissao;
import br.com.itau.geradornotafiscal.application.port.in.command.GerarNotaFiscalCommand;
import br.com.itau.geradornotafiscal.application.port.out.NotaFiscalPersistenciaPort;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import br.com.itau.geradornotafiscal.domain.entity.Pedido;
import br.com.itau.geradornotafiscal.domain.entity.TarefaIntegracao;
import br.com.itau.geradornotafiscal.domain.service.frete.CalculadoraFrete;
import br.com.itau.geradornotafiscal.domain.service.guarda.PrazoDeGuarda;
import br.com.itau.geradornotafiscal.domain.service.tributacao.CalculadoraTributo;
import br.com.itau.geradornotafiscal.domain.service.tributacao.Tributacao;
import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import br.com.itau.geradornotafiscal.domain.valueobject.Endereco;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GerarNotaFiscalUseCaseImpl implements GerarNotaFiscalUseCase {

    // Envios simultâneos: a nota do outro envio pode levar alguns milissegundos para ficar visível (E03-NF-01).
    private static final int TENTATIVAS_APOS_CONFLITO = 3;
    private static final long ESPERA_ENTRE_TENTATIVAS_MS = 50;

    private final Tributacao tributacao;
    private final CalculadoraTributo calculadoraTributo;
    private final CalculadoraFrete calculadoraFrete;
    private final Clock relogio;
    private final PrazoDeGuarda prazoDeGuarda;
    private final NotaFiscalPersistenciaPort notaFiscalPersistenciaPort;
    private final ReenvioUseCase reenvioUseCase;

    @Override
    public ResultadoDaEmissao gerarNotaFiscal(GerarNotaFiscalCommand comando) {
        // Com nota já emitida, vale o reenvio, sem conferir o restante do pedido (E03-RN-01, Q-15).
        Optional<NotaFiscal> jaEmitida = reenvioUseCase.notaDoReenvio(comando.idPedido(), comando.hashPedido());
        if (jaEmitida.isPresent()) {
            return new ResultadoDaEmissao(jaEmitida.get(), true);
        }

        Pedido pedido = Pedido.criar(comando.idPedido(), comando.data(), comando.valorTotalItens(), comando.valorFrete(),
                comando.itens(), comando.destinatario());

        Destinatario destinatario = pedido.getDestinatario();
        BigDecimal aliquota = tributacao.aliquota(destinatario, pedido.getValorTotalItens());
        Endereco entrega = destinatario.enderecoDeEntrega().orElseThrow();

        NotaFiscal notaFiscal = NotaFiscal.emitir(
                pedido,
                calculadoraTributo.calcular(pedido.getItens(), aliquota),
                calculadoraFrete.calcular(pedido.getValorFrete(), entrega.regiao()),
                LocalDateTime.now(relogio));

        // A nota e os quatro acionamentos são guardados juntos; os sistemas são acionados depois da resposta (E02-RN-01, E02-RN-02).
        List<TarefaIntegracao> tarefas = Arrays.stream(Sistema.values())
                .map(sistema -> TarefaIntegracao.criarPendente(pedido.getIdPedido(), sistema, relogio.instant()))
                .toList();
        try {
            notaFiscalPersistenciaPort.guardar(pedido.getIdPedido(), notaFiscal, comando.hashPedido(),
                    prazoDeGuarda.apagarAPartirDe(notaFiscal.getData()), tarefas);
        } catch (NotaJaGuardadaException | ConflitoDeGravacaoException outroEnvioGravouAntes) {
            // Só uma gravação vence; os demais envios seguem o caminho do reenvio (E03-RN-05).
            return new ResultadoDaEmissao(notaDoOutroEnvio(comando, outroEnvioGravouAntes), true);
        }

        return new ResultadoDaEmissao(notaFiscal, false);
    }

    private NotaFiscal notaDoOutroEnvio(GerarNotaFiscalCommand comando, RuntimeException conflito) {
        for (int tentativa = 1; tentativa <= TENTATIVAS_APOS_CONFLITO; tentativa++) {
            Optional<NotaFiscal> nota = reenvioUseCase.notaDoReenvio(comando.idPedido(), comando.hashPedido());
            if (nota.isPresent()) {
                return nota.get();
            }
            esperar(tentativa * ESPERA_ENTRE_TENTATIVAS_MS);
        }
        // A outra gravação não terminou: o consumidor pode reenviar (E04-NF-02).
        throw new ArmazenamentoIndisponivelException(conflito);
    }

    private static void esperar(long milissegundos) {
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
