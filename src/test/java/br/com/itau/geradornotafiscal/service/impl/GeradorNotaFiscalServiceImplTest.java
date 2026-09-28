package br.com.itau.geradornotafiscal.service.impl;

import br.com.itau.geradornotafiscal.model.Destinatario;
import br.com.itau.geradornotafiscal.model.Documento;
import br.com.itau.geradornotafiscal.model.Endereco;
import br.com.itau.geradornotafiscal.model.Finalidade;
import br.com.itau.geradornotafiscal.model.Item;
import br.com.itau.geradornotafiscal.model.NotaFiscal;
import br.com.itau.geradornotafiscal.model.Pedido;
import br.com.itau.geradornotafiscal.model.Regiao;
import br.com.itau.geradornotafiscal.model.TipoDocumento;
import br.com.itau.geradornotafiscal.model.TipoPessoa;
import br.com.itau.geradornotafiscal.service.CalculadoraAliquotaProduto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GeradorNotaFiscalServiceImplTest {

    @Mock
    private EstoqueService estoqueService;
    @Mock
    private RegistroService registroService;
    @Mock
    private EntregaService entregaService;
    @Mock
    private FinanceiroService financeiroService;

    private GeradorNotaFiscalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GeradorNotaFiscalServiceImpl(new CalculadoraAliquotaProduto(),
                estoqueService, registroService, entregaService, financeiroService);
    }

    @Test
    @DisplayName("E01-NF-05: dez chamadas seguidas do mesmo pedido, a entrega recebe 1 linha de item em cada")
    void e01Nf05_chamadasSeguidasNaoAumentamItensDaEntrega() {
        Pedido pedido = pedido("item", 1);

        for (int chamada = 0; chamada < 10; chamada++) {
            service.gerarNotaFiscal(pedido);
        }

        ArgumentCaptor<NotaFiscal> notas = ArgumentCaptor.forClass(NotaFiscal.class);
        verify(entregaService, times(10)).agendarEntrega(notas.capture());
        notas.getAllValues().forEach(nota -> assertEquals(1, nota.getItens().size()));
    }

    @Test
    @DisplayName("E01 cálculo #28 (E01-RN-17, E01-RN-18): mesmo pedido duas vezes gera duas notas, cada uma com 1 item")
    void e01Calculo28_mesmoPedidoDuasVezes() {
        Pedido pedido = pedido("item", 1);

        NotaFiscal primeira = service.gerarNotaFiscal(pedido);
        NotaFiscal segunda = service.gerarNotaFiscal(pedido);

        assertNotEquals(primeira.getIdNotaFiscal(), segunda.getIdNotaFiscal());
        assertEquals(1, primeira.getItens().size());
        assertEquals(1, segunda.getItens().size());
    }

    @Test
    @DisplayName("E01 cálculo #29 (E01-RN-18): pedido enviado antes da resposta do anterior não mistura itens")
    void e01Calculo29_pedidoEnviadoAntesDaRespostaDoAnterior() throws Exception {
        Pedido pedidoDeUmItem = pedido("primeiro", 1);
        Pedido pedidoDeTresItens = pedido("segundo", 3);
        CountDownLatch primeiroEmAndamento = new CountDownLatch(1);
        CountDownLatch segundoConcluido = new CountDownLatch(1);
        doAnswer(invocacao -> {
            primeiroEmAndamento.countDown();
            assertTrue(segundoConcluido.await(10, TimeUnit.SECONDS));
            return null;
        }).doNothing().when(estoqueService).enviarNotaFiscalParaBaixaEstoque(any());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<NotaFiscal> primeira = executor.submit(() -> service.gerarNotaFiscal(pedidoDeUmItem));
            assertTrue(primeiroEmAndamento.await(10, TimeUnit.SECONDS));
            NotaFiscal segunda = service.gerarNotaFiscal(pedidoDeTresItens);
            segundoConcluido.countDown();

            assertEquals(List.of("primeiro-0"), ids(primeira.get(10, TimeUnit.SECONDS)));
            assertEquals(List.of("segundo-0", "segundo-1", "segundo-2"), ids(segunda));
        } finally {
            executor.shutdownNow();
        }
    }

    private static Pedido pedido(String prefixo, int quantidadeDeItens) {
        List<Item> itens = IntStream.range(0, quantidadeDeItens)
                .mapToObj(i -> new Item(prefixo + "-" + i, "Teclado USB", 50, 2))
                .collect(Collectors.toList());
        Endereco entrega = Endereco.builder().finalidade(Finalidade.ENTREGA).regiao(Regiao.SUDESTE).build();
        Destinatario destinatario = Destinatario.builder()
                .nome("Fulano")
                .tipoPessoa(TipoPessoa.FISICA)
                .documentos(List.of(new Documento("887.403.470-95", TipoDocumento.CPF)))
                .enderecos(List.of(entrega))
                .build();
        return Pedido.builder()
                .idPedido(1)
                .valorTotalItens(100.0 * quantidadeDeItens)
                .valorFrete(10)
                .itens(itens)
                .destinatario(destinatario)
                .build();
    }

    private static List<String> ids(NotaFiscal nota) {
        return nota.getItens().stream().map(item -> item.getIdItem()).collect(Collectors.toList());
    }
}
