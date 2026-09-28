package br.com.itau.geradornotafiscal.application.usecase;

import tools.jackson.databind.json.JsonMapper;
import br.com.itau.geradornotafiscal.PedidoBase;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.PedidoRequest;
import br.com.itau.geradornotafiscal.adapter.in.web.mappers.PedidoMapper;
import br.com.itau.geradornotafiscal.application.exception.ArmazenamentoIndisponivelException;
import br.com.itau.geradornotafiscal.application.exception.ConflitoDeGravacaoException;
import br.com.itau.geradornotafiscal.application.exception.NotaJaGuardadaException;
import br.com.itau.geradornotafiscal.application.port.in.result.ResultadoDaEmissao;
import br.com.itau.geradornotafiscal.application.port.out.NotaFiscalPersistenciaPort;
import br.com.itau.geradornotafiscal.application.port.out.NotaFiscalPersistenciaPort.NotaGuardada;
import br.com.itau.geradornotafiscal.domain.valueobject.Item;
import br.com.itau.geradornotafiscal.domain.valueobject.ItemNotaFiscal;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import br.com.itau.geradornotafiscal.domain.entity.TarefaIntegracao;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import br.com.itau.geradornotafiscal.domain.valueobject.StatusTarefa;
import br.com.itau.geradornotafiscal.application.port.in.command.GerarNotaFiscalCommand;
import br.com.itau.geradornotafiscal.domain.service.tributacao.CalculadoraTributo;
import br.com.itau.geradornotafiscal.domain.service.frete.CalculadoraFrete;
import br.com.itau.geradornotafiscal.domain.service.guarda.PrazoDeGuarda;
import br.com.itau.geradornotafiscal.domain.service.reenvio.RegraDoReenvio;
import br.com.itau.geradornotafiscal.domain.service.tributacao.RegraLucroPresumido;
import br.com.itau.geradornotafiscal.domain.service.tributacao.RegraLucroReal;
import br.com.itau.geradornotafiscal.domain.service.tributacao.RegraPessoaFisica;
import br.com.itau.geradornotafiscal.domain.service.tributacao.RegraSimplesNacional;
import br.com.itau.geradornotafiscal.domain.service.tributacao.Tributacao;
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
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GerarNotaFiscalUseCaseImplTest {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-01-15T15:30:00Z"), SAO_PAULO);

    @Mock
    private NotaFiscalPersistenciaPort notaFiscalPersistenciaPort;
    @Captor
    private ArgumentCaptor<List<TarefaIntegracao>> tarefas;

    private GerarNotaFiscalUseCaseImpl service;

    @BeforeEach
    void setUp() {
        Tributacao tributacao = new Tributacao(List.of(
                new RegraPessoaFisica(), new RegraSimplesNacional(), new RegraLucroReal(), new RegraLucroPresumido()));
        service = new GerarNotaFiscalUseCaseImpl(tributacao, new CalculadoraTributo(), new CalculadoraFrete(), RELOGIO, new PrazoDeGuarda(), notaFiscalPersistenciaPort, new RegraDoReenvio());
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
        GerarNotaFiscalCommand pedido = pedido(mudanca.apply(PedidoBase.novo()));

        NotaFiscal nota = service.executar(pedido).nota();

        if (tributos != null) {
            assertEquals(tributos, nota.getItens().stream()
                    .map(item -> String.valueOf(item.valorTributoItem())).collect(Collectors.toList()));
        }
        if (frete != null) {
            assertEquals(frete, String.valueOf(nota.getValorFrete()));
        }
        // E01-RN-17: total e itens como recebidos, na mesma ordem, com 2 casas.
        assertEquals(pedido.valorTotalItens().setScale(2).toPlainString(), String.valueOf(nota.getValorTotalItens()));
        assertEquals(pedido.itens().size(), nota.getItens().size());
        for (int i = 0; i < pedido.itens().size(); i++) {
            Item recebido = pedido.itens().get(i);
            ItemNotaFiscal devolvido = nota.getItens().get(i);
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
            NotaFiscal nota = service.executar(pedido(PedidoBase.novo())).nota();

            assertEquals(LocalDateTime.of(2026, 1, 15, 12, 30), nota.getData());
        } finally {
            TimeZone.setDefault(fusoDaMaquina);
        }
    }

    @Test
    @DisplayName("E02-RN-01, E02-RN-02, E04-RN-03: a nota é guardada com a data de expurgo e uma tarefa pendente por sistema, e a resposta não espera os sistemas")
    void e02Rn02_notaGuardadaComQuatroTarefasPendentes() {
        GerarNotaFiscalCommand pedido = pedido(PedidoBase.novo());

        NotaFiscal nota = service.executar(pedido).nota();

        verify(notaFiscalPersistenciaPort).guardar(eq(pedido.idPedido()), eq(nota), eq(pedido.hashPedido()),
                eq(LocalDate.of(2032, 1, 1)), tarefas.capture());
        assertEquals(List.of(Sistema.values()), tarefas.getValue().stream().map(TarefaIntegracao::getSistema).toList());
        tarefas.getValue().forEach(tarefa -> {
            assertEquals(StatusTarefa.PENDENTE, tarefa.getStatus());
            assertEquals(pedido.idPedido(), tarefa.getIdPedido());
            assertEquals(RELOGIO.instant(), tarefa.getPendenteDesde());
        });
    }

    @Test
    @DisplayName("E04-RN-01: se a nota não é guardada, a recusa chega ao consumidor")
    void e04Rn01_semGuarda() {
        GerarNotaFiscalCommand pedido = pedido(PedidoBase.novo());
        doThrow(new ArmazenamentoIndisponivelException(new IllegalStateException()))
                .when(notaFiscalPersistenciaPort).guardar(any(), any(), any(), any(), any());

        assertThrows(ArmazenamentoIndisponivelException.class, () -> service.executar(pedido));
    }

    @Test
    @DisplayName("E01-NF-05: dez chamadas seguidas do mesmo conteúdo geram notas de 1 linha de item cada")
    void e01Nf05_chamadasSeguidasNaoAumentamItens() {
        for (int chamada = 0; chamada < 10; chamada++) {
            service.executar(pedido(PedidoBase.novo()));
        }

        ArgumentCaptor<NotaFiscal> notas = ArgumentCaptor.forClass(NotaFiscal.class);
        verify(notaFiscalPersistenciaPort, times(10)).guardar(any(), notas.capture(), any(), any(), any());
        notas.getAllValues().forEach(nota -> assertEquals(1, nota.getItens().size()));
    }

    @Test
    @DisplayName("E01 cálculo #28, substituído pelo E03 exemplo 1 (E03-RN-02): mesmo pedido duas vezes devolve a mesma nota, com 1 item, e cria as tarefas uma vez")
    void e01Calculo28_mesmoPedidoDuasVezes() {
        GerarNotaFiscalCommand pedido = pedido(PedidoBase.novo());

        NotaFiscal primeira = service.executar(pedido).nota();
        when(notaFiscalPersistenciaPort.buscar(pedido.idPedido())).thenReturn(Optional.of(new NotaGuardada(primeira, pedido.hashPedido())));
        ResultadoDaEmissao segunda = service.executar(pedido);

        assertTrue(segunda.reenvio());
        assertSame(primeira, segunda.nota());
        assertEquals(1, segunda.nota().getItens().size());
        verify(notaFiscalPersistenciaPort, times(1)).guardar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("E03-RN-01 (Q-15): com nota já emitida, o pedido não é conferido de novo, mesmo que hoje fosse recusado")
    void e03Rn01_notaJaEmitidaNaoConfereOPedido() {
        GerarNotaFiscalCommand pedidoHojeRecusado = pedido(frete(PedidoBase.novo(), "-1.00"));
        NotaFiscal jaEmitida = service.executar(pedido(PedidoBase.novo())).nota();
        when(notaFiscalPersistenciaPort.buscar(pedidoHojeRecusado.idPedido()))
                .thenReturn(Optional.of(new NotaGuardada(jaEmitida, pedidoHojeRecusado.hashPedido())));

        ResultadoDaEmissao resultado = service.executar(pedidoHojeRecusado);

        assertTrue(resultado.reenvio());
        assertSame(jaEmitida, resultado.nota());
    }

    @Test
    @DisplayName("E03-RN-05: outro envio gravou antes; a nota dele é devolvida, sem tarefas novas")
    void e03Rn05_outroEnvioGravouAntes() {
        GerarNotaFiscalCommand pedido = pedido(PedidoBase.novo());
        NotaFiscal doOutroEnvio = service.executar(pedido(PedidoBase.novo())).nota();
        doThrow(new NotaJaGuardadaException()).when(notaFiscalPersistenciaPort).guardar(eq(pedido.idPedido()), any(), any(), any(), any());
        when(notaFiscalPersistenciaPort.buscar(pedido.idPedido()))
                .thenReturn(Optional.empty(), Optional.of(new NotaGuardada(doOutroEnvio, pedido.hashPedido())));

        ResultadoDaEmissao resultado = service.executar(pedido);

        assertTrue(resultado.reenvio());
        assertSame(doOutroEnvio, resultado.nota());
    }

    @Test
    @DisplayName("E03-NF-01: conflito de gravação (simulado: o emulador não o gera) tenta ler de novo até a nota do outro envio aparecer")
    void e03Nf01_conflitoDeGravacaoTentaDeNovo() {
        GerarNotaFiscalCommand pedido = pedido(PedidoBase.novo());
        NotaFiscal doOutroEnvio = service.executar(pedido(PedidoBase.novo())).nota();
        doThrow(new ConflitoDeGravacaoException(new IllegalStateException()))
                .when(notaFiscalPersistenciaPort).guardar(eq(pedido.idPedido()), any(), any(), any(), any());
        when(notaFiscalPersistenciaPort.buscar(pedido.idPedido()))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(new NotaGuardada(doOutroEnvio, pedido.hashPedido())));

        assertSame(doOutroEnvio, service.executar(pedido).nota());
    }

    @Test
    @DisplayName("E03-NF-01, E04-NF-02: se a outra gravação não termina, responde indisponível para o consumidor reenviar")
    void e03Nf01_conflitoQueNaoTermina() {
        GerarNotaFiscalCommand pedido = pedido(PedidoBase.novo());
        doThrow(new ConflitoDeGravacaoException(new IllegalStateException()))
                .when(notaFiscalPersistenciaPort).guardar(any(), any(), any(), any(), any());

        assertThrows(ArmazenamentoIndisponivelException.class, () -> service.executar(pedido));
    }

    @Test
    @DisplayName("E01 cálculo #29 (E01-RN-18): pedido enviado antes da resposta do anterior não mistura itens")
    void e01Calculo29_pedidoEnviadoAntesDaRespostaDoAnterior() throws Exception {
        GerarNotaFiscalCommand pedidoDeUmItem = pedido(comItens("primeiro", 1));
        GerarNotaFiscalCommand pedidoDeTresItens = pedido(comItens("segundo", 3));
        CountDownLatch primeiroEmAndamento = new CountDownLatch(1);
        CountDownLatch segundoConcluido = new CountDownLatch(1);
        doAnswer(invocacao -> {
            primeiroEmAndamento.countDown();
            assertTrue(segundoConcluido.await(10, TimeUnit.SECONDS));
            return null;
        }).doNothing().when(notaFiscalPersistenciaPort).guardar(any(), any(), any(), any(), any());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<NotaFiscal> primeira = executor.submit(() -> service.executar(pedidoDeUmItem).nota());
            assertTrue(primeiroEmAndamento.await(10, TimeUnit.SECONDS));
            NotaFiscal segunda = service.executar(pedidoDeTresItens).nota();
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

    private static GerarNotaFiscalCommand pedido(ObjectNode json) {
        return new PedidoMapper().paraComando(PedidoBase.converter(OBJECT_MAPPER, json, PedidoRequest.class), "hash-" + json.hashCode());
    }

    private static List<String> ids(NotaFiscal nota) {
        return nota.getItens().stream().map(item -> item.idItem()).collect(Collectors.toList());
    }
}
