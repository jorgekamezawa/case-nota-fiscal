package br.com.itau.geradornotafiscal.application.usecase;

import tools.jackson.databind.json.JsonMapper;
import br.com.itau.geradornotafiscal.PedidoBase;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.PedidoRequest;
import br.com.itau.geradornotafiscal.adapter.in.web.mappers.PedidoMapper;
import br.com.itau.geradornotafiscal.application.port.out.EntregaPort;
import br.com.itau.geradornotafiscal.application.port.out.EstoquePort;
import br.com.itau.geradornotafiscal.application.port.out.FinanceiroPort;
import br.com.itau.geradornotafiscal.application.port.out.RegistroPort;
import br.com.itau.geradornotafiscal.domain.valueobject.Item;
import br.com.itau.geradornotafiscal.domain.valueobject.ItemNotaFiscal;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import br.com.itau.geradornotafiscal.domain.entity.Pedido;
import br.com.itau.geradornotafiscal.domain.service.tributacao.CalculadoraTributo;
import br.com.itau.geradornotafiscal.domain.service.frete.CalculadoraFrete;
import br.com.itau.geradornotafiscal.domain.service.tributacao.RegraLucroPresumido;
import br.com.itau.geradornotafiscal.domain.service.tributacao.RegraLucroReal;
import br.com.itau.geradornotafiscal.domain.service.tributacao.RegraPessoaFisica;
import br.com.itau.geradornotafiscal.domain.service.tributacao.RegraSimplesNacional;
import br.com.itau.geradornotafiscal.domain.service.tributacao.Tributacao;
import br.com.itau.geradornotafiscal.domain.service.validacao.RegrasDoPedido;
import br.com.itau.geradornotafiscal.domain.service.validacao.ValidadorDocumento;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static br.com.itau.geradornotafiscal.PedidoBase.endereco;
import static br.com.itau.geradornotafiscal.PedidoBase.enderecos;
import static br.com.itau.geradornotafiscal.PedidoBase.frete;
import static br.com.itau.geradornotafiscal.PedidoBase.item;
import static br.com.itau.geradornotafiscal.PedidoBase.itens;
import static br.com.itau.geradornotafiscal.PedidoBase.pj;
import static br.com.itau.geradornotafiscal.PedidoBase.umItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GerarNotaFiscalUseCaseImplTest {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-01-15T15:30:00Z"), SAO_PAULO);

    @Mock
    private EstoquePort estoquePort;
    @Mock
    private RegistroPort registroPort;
    @Mock
    private EntregaPort entregaPort;
    @Mock
    private FinanceiroPort financeiroPort;

    private GerarNotaFiscalUseCaseImpl service;

    @BeforeEach
    void setUp() {
        Tributacao tributacao = new Tributacao(List.of(
                new RegraPessoaFisica(), new RegraSimplesNacional(), new RegraLucroReal(), new RegraLucroPresumido()));
        service = new GerarNotaFiscalUseCaseImpl(new RegrasDoPedido(new ValidadorDocumento()), tributacao,
                new CalculadoraTributo(), new CalculadoraFrete(), RELOGIO, estoquePort, registroPort, entregaPort, financeiroPort);
    }

    static Stream<Arguments> exemplosDeCalculo() {
        return Stream.of(
                exemplo("#1 (E01-RN-11, E01-RN-15)", p -> p, List.of("0.00"), "10.48"),
                exemplo("#2 (E01-RN-11)", p -> umItem(p, "499.99", 1), List.of("0.00"), null),
                exemplo("#3 (E01-RN-11, E01-RN-14)", p -> umItem(p, "250.00", 2), List.of("60.00"), null),
                exemplo("#4 (E01-RN-11)", p -> umItem(p, "2000.00", 1), List.of("240.00"), null),
                exemplo("#5 (E01-RN-11, E01-RN-16)", p -> umItem(p, "2000.01", 1), List.of("300.00"), null),
                exemplo("#6 (E01-RN-11)", p -> umItem(p, "3500.00", 1), List.of("525.00"), null),
                exemplo("#7 (E01-RN-11, E01-RN-16)", p -> umItem(p, "3500.01", 1), List.of("595.00"), null),
                exemplo("#8 (E01-RN-12, E01-RN-16)", p -> umItem(pj(p, "SIMPLES_NACIONAL"), "999.99", 1), List.of("30.00"), null),
                exemplo("#9 (E01-RN-12)", p -> umItem(pj(p, "SIMPLES_NACIONAL"), "1000.00", 1), List.of("70.00"), null),
                exemplo("#10 (E01-RN-12)", p -> umItem(pj(p, "LUCRO_REAL"), "1000.00", 1), List.of("90.00"), null),
                exemplo("#11 (E01-RN-12, E01-RN-16)", p -> umItem(pj(p, "LUCRO_REAL"), "2000.01", 1), List.of("300.00"), null),
                exemplo("#12 (E01-RN-12)", p -> umItem(pj(p, "LUCRO_PRESUMIDO"), "3000.00", 1), List.of("480.00"), null),
                exemplo("#13 (E01-RN-12)", p -> umItem(pj(p, "SIMPLES_NACIONAL"), "5000.00", 1), List.of("650.00"), null),
                exemplo("#14 (E01-RN-12, E01-RN-14, E01-RN-15)",
                        p -> frete(umItem(pj(p, "SIMPLES_NACIONAL"), "730.00", 8), "72.00"), List.of("1109.60"), "75.46"),
                exemplo("#15 (E01-RN-12)", p -> umItem(pj(p, "LUCRO_REAL"), "730.00", 8), List.of("1168.00"), null),
                exemplo("#16 (E01-RN-13, E01-RN-17)",
                        p -> itens(p, item("A", "300.00", 1), item("B", "250.00", 2)), List.of("36.00", "60.00"), null),
                exemplo("#17 (E01-RN-16)", p -> umItem(pj(p, "SIMPLES_NACIONAL"), "11.50", 1), List.of("0.34"), null),
                exemplo("#18 (E01-RN-16)", p -> umItem(pj(p, "SIMPLES_NACIONAL"), "12.50", 1), List.of("0.38"), null),
                exemplo("#19 (E01-RN-15, E01-RN-16)",
                        p -> enderecos(frete(p, "1.00"), endereco("ENTREGA", "NORDESTE")), null, "1.08"),
                exemplo("#20 (E01-RN-15)", p -> enderecos(p, endereco("ENTREGA", "CENTRO_OESTE")), null, "10.70"),
                exemplo("#21 (E01-RN-15)", p -> frete(p, "0.00"), null, "0.00"),
                exemplo("#22 (E01-RN-15)",
                        p -> enderecos(p, endereco("COBRANCA", "NORTE"), endereco("ENTREGA", "SUL")), null, "10.60"),
                exemplo("#23 (E01-RN-15)",
                        p -> enderecos(p, endereco("ENTREGA", "NORTE"), endereco("ENTREGA", "SUL")), null, "10.80"),
                exemplo("#24 (E01-RN-15)", p -> enderecos(p, endereco("COBRANCA_ENTREGA", "NORTE")), null, "10.80"),
                exemplo("#30 (E01-RN-12, E01-RN-16)", p -> umItem(pj(p, "LUCRO_REAL"), "999.99", 1), List.of("30.00"), null),
                exemplo("#31 (E01-RN-12, E01-RN-16)", p -> umItem(pj(p, "LUCRO_PRESUMIDO"), "999.99", 1), List.of("30.00"), null),
                exemplo("#32 (E01-RN-12)", p -> umItem(pj(p, "LUCRO_PRESUMIDO"), "1000.00", 1), List.of("90.00"), null),
                exemplo("#33 (E01-RN-12, E01-RN-16)", p -> umItem(pj(p, "LUCRO_PRESUMIDO"), "5000.01", 1), List.of("1000.00"), null),
                exemplo("#34 (E01-RN-16)", p -> umItem(pj(p, "SIMPLES_NACIONAL"), "11.51", 1), List.of("0.35"), null));
    }

    @ParameterizedTest(name = "E01 cálculo {0}")
    @MethodSource("exemplosDeCalculo")
    void e01ExemplosDeCalculo(String exemplo, UnaryOperator<ObjectNode> mudanca, List<String> tributos, String frete) {
        Pedido pedido = pedido(mudanca.apply(PedidoBase.novo()));

        NotaFiscal nota = service.gerarNotaFiscal(pedido);

        if (tributos != null) {
            assertEquals(tributos, nota.itens().stream()
                    .map(item -> String.valueOf(item.valorTributoItem())).collect(Collectors.toList()));
        }
        if (frete != null) {
            assertEquals(frete, String.valueOf(nota.valorFrete()));
        }
        // E01-RN-17: total e itens como recebidos, na mesma ordem, com 2 casas.
        assertEquals(pedido.valorTotalItens().setScale(2).toPlainString(), String.valueOf(nota.valorTotalItens()));
        assertEquals(pedido.itens().size(), nota.itens().size());
        for (int i = 0; i < pedido.itens().size(); i++) {
            Item recebido = pedido.itens().get(i);
            ItemNotaFiscal devolvido = nota.itens().get(i);
            assertEquals(recebido.idItem(), devolvido.idItem());
            assertEquals(recebido.descricao(), devolvido.descricao());
            assertEquals(recebido.valorUnitario().setScale(2).toPlainString(), String.valueOf(devolvido.valorUnitario()));
            assertEquals(recebido.quantidade(), devolvido.quantidade());
        }
    }

    @Test
    @DisplayName("E01 cálculo #25 (E01-RN-17, E01-NF-02): data da nota é o momento da geração, no horário de São Paulo")
    void e01Calculo25_dataDaGeracaoNoHorarioDeSaoPaulo() {
        TimeZone fusoDaMaquina = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
        try {
            NotaFiscal nota = service.gerarNotaFiscal(pedido(PedidoBase.novo()));

            assertEquals(LocalDateTime.of(2026, 1, 15, 12, 30), nota.data());
        } finally {
            TimeZone.setDefault(fusoDaMaquina);
        }
    }

    @Test
    @DisplayName("E01-NF-05: dez chamadas seguidas do mesmo pedido, a entrega recebe 1 linha de item em cada")
    void e01Nf05_chamadasSeguidasNaoAumentamItensDaEntrega() {
        Pedido pedido = pedido(PedidoBase.novo());

        for (int chamada = 0; chamada < 10; chamada++) {
            service.gerarNotaFiscal(pedido);
        }

        ArgumentCaptor<NotaFiscal> notas = ArgumentCaptor.forClass(NotaFiscal.class);
        verify(entregaPort, times(10)).agendarEntrega(notas.capture());
        notas.getAllValues().forEach(nota -> assertEquals(1, nota.itens().size()));
    }

    @Test
    @DisplayName("E01 cálculo #28 (E01-RN-17, E01-RN-18): mesmo pedido duas vezes gera duas notas, cada uma com 1 item")
    void e01Calculo28_mesmoPedidoDuasVezes() {
        Pedido pedido = pedido(PedidoBase.novo());

        NotaFiscal primeira = service.gerarNotaFiscal(pedido);
        NotaFiscal segunda = service.gerarNotaFiscal(pedido);

        assertNotEquals(primeira.idNotaFiscal(), segunda.idNotaFiscal());
        assertEquals(1, primeira.itens().size());
        assertEquals(1, segunda.itens().size());
    }

    @Test
    @DisplayName("E01 cálculo #29 (E01-RN-18): pedido enviado antes da resposta do anterior não mistura itens")
    void e01Calculo29_pedidoEnviadoAntesDaRespostaDoAnterior() throws Exception {
        Pedido pedidoDeUmItem = pedido(comItens("primeiro", 1));
        Pedido pedidoDeTresItens = pedido(comItens("segundo", 3));
        CountDownLatch primeiroEmAndamento = new CountDownLatch(1);
        CountDownLatch segundoConcluido = new CountDownLatch(1);
        doAnswer(invocacao -> {
            primeiroEmAndamento.countDown();
            assertTrue(segundoConcluido.await(10, TimeUnit.SECONDS));
            return null;
        }).doNothing().when(estoquePort).enviarNotaFiscalParaBaixaEstoque(any());

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

    private static Arguments exemplo(String nome, UnaryOperator<ObjectNode> mudanca, List<String> tributos, String frete) {
        return Arguments.of(nome, mudanca, tributos, frete);
    }

    private static ObjectNode comItens(String prefixo, int quantidadeDeItens) {
        return itens(PedidoBase.novo(), IntStream.range(0, quantidadeDeItens)
                .mapToObj(i -> item(prefixo + "-" + i, "50.00", 2)).toArray(ObjectNode[]::new));
    }

    private static Pedido pedido(ObjectNode json) {
        return new PedidoMapper().paraDominio(PedidoBase.converter(OBJECT_MAPPER, json, PedidoRequest.class));
    }

    private static List<String> ids(NotaFiscal nota) {
        return nota.itens().stream().map(item -> item.idItem()).collect(Collectors.toList());
    }
}
