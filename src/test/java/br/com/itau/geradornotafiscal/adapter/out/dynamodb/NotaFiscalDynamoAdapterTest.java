package br.com.itau.geradornotafiscal.adapter.out.dynamodb;

import br.com.itau.geradornotafiscal.PedidoBase;
import br.com.itau.geradornotafiscal.adapter.out.dynamodb.mappers.NotaFiscalRegistroMapper;
import br.com.itau.geradornotafiscal.adapter.out.dynamodb.mappers.TarefaIntegracaoRegistroMapper;
import br.com.itau.geradornotafiscal.application.exception.ArmazenamentoIndisponivelException;
import br.com.itau.geradornotafiscal.application.exception.ConflitoDeGravacaoException;
import br.com.itau.geradornotafiscal.application.exception.NotaGrandeDemaisException;
import br.com.itau.geradornotafiscal.application.exception.NotaJaGuardadaException;
import br.com.itau.geradornotafiscal.application.port.out.NotaFiscalPersistenciaPort.NotaGuardada;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import br.com.itau.geradornotafiscal.domain.valueobject.Documento;
import br.com.itau.geradornotafiscal.domain.valueobject.Endereco;
import br.com.itau.geradornotafiscal.domain.valueobject.Finalidade;
import br.com.itau.geradornotafiscal.domain.valueobject.ItemNotaFiscal;
import br.com.itau.geradornotafiscal.domain.valueobject.Regiao;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoDocumento;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoPessoa;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guarda das notas contra o emulador oficial do DynamoDB (E04-NF-05).
 */
@SpringBootTest
class NotaFiscalDynamoAdapterTest {

    private static final LocalDate APAGAR_EM = LocalDate.of(2032, 1, 1);
    private static final String HASH = "a".repeat(64);

    @Autowired
    private NotaFiscalDynamoAdapter adapter;
    @Autowired
    private DynamoDbClient dynamoDb;
    @Autowired
    private NotaFiscalRegistroMapper mapper;

    @Test
    @DisplayName("E04-RN-02: a nota lida é igual à guardada, com 2 casas, nulos e milissegundos preservados")
    void e04Rn02_notaLidaIgualAGuardada() {
        long idPedido = PedidoBase.novoId();
        NotaFiscal nota = nota(1, "Teclado USB");

        adapter.guardar(idPedido, nota, HASH, APAGAR_EM, List.of());
        NotaGuardada guardada = adapter.buscar(idPedido).orElseThrow();
        NotaFiscal lida = guardada.nota();

        assertThat(lida).usingRecursiveComparison().isEqualTo(nota);
        assertEquals("100.00", lida.getValorTotalItens().toPlainString());
        assertEquals("0.00", lida.getItens().get(0).valorTributoItem().toPlainString());
        assertEquals(HASH, guardada.hashPedido());
    }

    @Test
    @DisplayName("E04-NF-01, E04-RN-03: data de expurgo em segundos, à meia-noite de São Paulo")
    void e04Nf01_dataDeExpurgoNoFormatoDoTtl() {
        long idPedido = PedidoBase.novoId();

        adapter.guardar(idPedido, nota(1, "Teclado USB"), HASH, APAGAR_EM, List.of());

        Map<String, AttributeValue> item = dynamoDb.getItem(r -> r.tableName("notas")
                .key(Map.of("id_pedido", AttributeValue.fromN(Long.toString(idPedido))))).item();
        assertEquals("1956538800", item.get("expira_em").n());
    }

    @Test
    @DisplayName("E03-NF-01: a segunda nota do mesmo pedido não é gravada")
    void e03Nf01_segundaNotaDoMesmoPedidoRecusada() {
        long idPedido = PedidoBase.novoId();
        adapter.guardar(idPedido, nota(1, "Teclado USB"), HASH, APAGAR_EM, List.of());

        assertThatThrownBy(() -> adapter.guardar(idPedido, nota(1, "Outro"), HASH, APAGAR_EM, List.of()))
                .isInstanceOf(NotaJaGuardadaException.class);
        assertEquals("Teclado USB", adapter.buscar(idPedido).orElseThrow().nota().getItens().get(0).descricao());
    }

    @Test
    @DisplayName("E04-RN-02: pedido sem nota não tem o que devolver")
    void e04Rn02_pedidoSemNota() {
        assertThat(adapter.buscar(PedidoBase.novoId())).isEmpty();
    }

    @Test
    @DisplayName("E04-RN-04: nota que não cabe é recusada antes de gravar")
    void e04Rn04_notaQueNaoCabe() {
        long idPedido = PedidoBase.novoId();

        assertThatThrownBy(() -> adapter.guardar(idPedido, nota(1, "x".repeat(410 * 1024)), HASH, APAGAR_EM, List.of()))
                .isInstanceOf(NotaGrandeDemaisException.class);
        assertThat(adapter.buscar(idPedido)).isEmpty();
    }

    @Test
    @DisplayName("E04-RN-01: banco fora do ar vira indisponibilidade, com métrica")
    void e04Rn01_bancoForaDoAr() {
        SimpleMeterRegistry metricas = new SimpleMeterRegistry();
        try (DynamoDbClient semBanco = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://localhost:1"))
                .region(Region.US_EAST_1)
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .build()) {
            NotaFiscalDynamoAdapter semArmazenamento = new NotaFiscalDynamoAdapter(semBanco, mapper, new TarefaIntegracaoRegistroMapper(), new ChamadasDynamoDb(metricas),
                    Clock.systemUTC(), "notas", "tarefas_integracao");

            assertThatThrownBy(() -> semArmazenamento.guardar(PedidoBase.novoId(), nota(1, "Teclado USB"), HASH, APAGAR_EM, List.of()))
                    .isInstanceOf(ArmazenamentoIndisponivelException.class);
        }
        assertEquals(1.0, metricas.counter("armazenamento.falhas", "operacao", "guardar").count());
    }

    @Test
    @DisplayName("E03-RN-06: nota vencida e ainda não apagada não é devolvida e dá lugar à nota nova")
    void e03Rn06_notaVencidaSubstituida() {
        long idPedido = PedidoBase.novoId();
        adapter.guardar(idPedido, nota(1, "Vencida"), HASH, LocalDate.of(2020, 1, 1), List.of());

        assertThat(adapter.buscar(idPedido)).isEmpty();

        adapter.guardar(idPedido, nota(1, "Nova"), HASH, APAGAR_EM, List.of());
        assertEquals("Nova", adapter.buscar(idPedido).orElseThrow().nota().getItens().get(0).descricao());
    }

    @Test
    @DisplayName("E03-NF-01: conflito com outra gravação do mesmo pedido (simulado: o emulador não o gera)")
    void e03Nf01_conflitoDeGravacao() {
        DynamoDbClient emConflito = mock(DynamoDbClient.class);
        when(emConflito.transactWriteItems(ArgumentMatchers.<Consumer<TransactWriteItemsRequest.Builder>>any()))
                .thenThrow(TransactionCanceledException.builder().message("conflito")
                        .cancellationReasons(CancellationReason.builder().code("TransactionConflict").build()).build());
        NotaFiscalDynamoAdapter comConflito =
                new NotaFiscalDynamoAdapter(emConflito, mapper, new TarefaIntegracaoRegistroMapper(),
                new ChamadasDynamoDb(new SimpleMeterRegistry()), Clock.systemUTC(), "notas", "tarefas_integracao");

        assertThatThrownBy(() -> comConflito.guardar(PedidoBase.novoId(), nota(1, "Teclado USB"), HASH, APAGAR_EM, List.of()))
                .isInstanceOf(ConflitoDeGravacaoException.class);
    }

    @Test
    @DisplayName("E03-NF-01: a nota existente é lida com leitura fortemente consistente (simulado: o emulador não a diferencia)")
    void e03Nf01_leituraFortementeConsistente() {
        DynamoDbClient cliente = mock(DynamoDbClient.class);
        ArgumentCaptor<Consumer<GetItemRequest.Builder>> requisicao = ArgumentCaptor.captor();
        when(cliente.getItem(requisicao.capture())).thenReturn(GetItemResponse.builder().build());
        NotaFiscalDynamoAdapter comMock = new NotaFiscalDynamoAdapter(cliente, mapper, new TarefaIntegracaoRegistroMapper(),
                new ChamadasDynamoDb(new SimpleMeterRegistry()), Clock.systemUTC(), "notas", "tarefas_integracao");

        comMock.buscar(PedidoBase.novoId());

        GetItemRequest.Builder construida = GetItemRequest.builder();
        requisicao.getValue().accept(construida);
        assertEquals(Boolean.TRUE, construida.build().consistentRead());
    }

    static NotaFiscal nota(int linhas, String descricao) {
        List<ItemNotaFiscal> itens = Collections.nCopies(linhas, new ItemNotaFiscal("1", descricao,
                new BigDecimal("50.00"), new BigDecimal("2"), new BigDecimal("0.00")));
        Destinatario destinatario = new Destinatario("Fulano de Tal", TipoPessoa.FISICA, null,
                List.of(new Documento(PedidoBase.CPF, TipoDocumento.CPF)),
                List.of(new Endereco("03105003", "Av do Estado", "5533", null, null, "SP", null, "4 andar b",
                        Finalidade.ENTREGA, Regiao.SUDESTE)));
        return NotaFiscal.reconstituir("7d1c5b1e-2f4a-4c1b-9a53-0c7c4e9b2a10", LocalDateTime.of(2026, 1, 15, 12, 30, 45, 123_000_000),
                new BigDecimal("100.00"), new BigDecimal("10.48"), itens, destinatario);
    }
}
