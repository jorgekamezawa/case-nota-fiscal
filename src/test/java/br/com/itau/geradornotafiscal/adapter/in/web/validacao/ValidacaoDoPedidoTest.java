package br.com.itau.geradornotafiscal.adapter.in.web.validacao;

import br.com.itau.geradornotafiscal.PedidoBase;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.PedidoRequest;
import br.com.itau.geradornotafiscal.adapter.in.web.mappers.PedidoMapper;
import br.com.itau.geradornotafiscal.domain.exception.PedidoInvalidoException;
import br.com.itau.geradornotafiscal.application.port.in.command.GerarNotaFiscalCommand;
import br.com.itau.geradornotafiscal.domain.entity.Pedido;
import br.com.itau.geradornotafiscal.config.JacksonConfig;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static br.com.itau.geradornotafiscal.PedidoBase.CNPJ;
import static br.com.itau.geradornotafiscal.PedidoBase.endereco;
import static br.com.itau.geradornotafiscal.PedidoBase.enderecos;
import static br.com.itau.geradornotafiscal.PedidoBase.item;
import static br.com.itau.geradornotafiscal.PedidoBase.pj;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tabela de exemplos de validação da spec E-01, rodando as duas etapas como a produção (E01-RN-09): na etapa 1, a
 * conversão do JSON (erros de tipo, um por vez) e as anotações de preenchimento; na etapa 2, as regras de negócio do
 * domínio, só quando a etapa 1 passa.
 */
class ValidacaoDoPedidoTest {

    private static final JsonMapper LEITOR = leitorDaAplicacao();
    private static final Validator VALIDADOR = Validation.buildDefaultValidatorFactory().getValidator();

    private final PedidoMapper pedidoMapper = new PedidoMapper();

    static Stream<Arguments> exemplosDeValidacao() {
        return Stream.of(
                exemplo("#1 (todas)", p -> p),
                exemplo("#2 (E01-RN-05)", p -> frete(p, "0.00")),
                exemplo("#3 (E01-RN-01)", p -> remover(p, "valor_frete"), "valor_frete:campo-obrigatorio"),
                exemplo("#4 (E01-RN-05)", p -> frete(p, "-1.00"), "valor_frete:frete-negativo"),
                exemplo("#5 (E01-RN-08)", p -> frete(p, "10.555"), "valor_frete:casas-decimais-excedidas"),
                exemplo("#6 (E01-RN-07)", p -> itemUnico(p, item("1", "5000.00", 1)), "valor_total_itens:total-divergente"),
                exemplo("#7 (E01-RN-04, E01-RN-07)", p -> {
                    item0(p).put("quantidade", new BigDecimal("2.7"));
                    return p;
                }, "itens[0].quantidade:quantidade-invalida"),
                exemplo("#8 (E01-RN-04, E01-RN-07)", p -> valorUnitario(p, "0.00"),
                        "itens[0].valor_unitario:valor-unitario-invalido"),
                exemplo("#9 (E01-RN-08, E01-RN-07)", p -> valorUnitario(p, "10.005"),
                        "itens[0].valor_unitario:casas-decimais-excedidas"),
                exemplo("#10 (E01-RN-01)", p -> {
                    destinatario(pj(p, "SIMPLES_NACIONAL")).remove("regime_tributacao");
                    return p;
                }, "destinatario.regime_tributacao:campo-obrigatorio"),
                exemplo("#11 (E01-RN-03)", p -> pj(p, "OUTROS"), "destinatario.regime_tributacao:regime-nao-atendido"),
                exemplo("#12 (E01-RN-03)", p -> regime(p, "LUCRO_REAL"), "destinatario.regime_tributacao:regime-nao-se-aplica"),
                exemplo("#13 (E01-RN-08)", p -> regime(p, "MEI"), "destinatario.regime_tributacao:valor-nao-aceito"),
                exemplo("#14 (E01-RN-02)", p -> documento(p, "CNPJ", CNPJ), "destinatario.documentos:documento-do-tipo-ausente"),
                exemplo("#15 (E01-RN-02)", p -> documento(p, "CPF", "887.403.470-96"),
                        "destinatario.documentos[0].numero:documento-invalido"),
                exemplo("#16 (E01-RN-02)", p -> documento(p, "CPF", "887.403.470-9"),
                        "destinatario.documentos[0].numero:documento-invalido"),
                exemplo("#17 (E01-RN-06)", p -> enderecos(p, endereco("COBRANCA", "SUDESTE")),
                        "destinatario.enderecos:sem-endereco-de-entrega"),
                exemplo("#18 (E01-RN-06)", p -> {
                    ObjectNode semRegiao = endereco("ENTREGA", "SUDESTE");
                    semRegiao.remove("regiao");
                    return enderecos(p, semRegiao, endereco("COBRANCA_ENTREGA", "SUL"));
                }, "destinatario.enderecos[0].regiao:campo-obrigatorio"),
                exemplo("#19 (E01-RN-08)", p -> enderecos(p, endereco("ENTREGA", "SUDESTE"), endereco("COBRANCA", "LESTE")),
                        "destinatario.enderecos[1].regiao:valor-nao-aceito"),
                exemplo("#20 (E01-RN-08)", p -> {
                    tipoPessoa(p, "ESTRANGEIRA");
                    return p;
                }, "destinatario.tipo_pessoa:valor-nao-aceito"),
                exemplo("#21 (E01-RN-10)", p -> remover(p, "destinatario"), "destinatario:campo-obrigatorio"),
                exemplo("#22 (E01-RN-04, E01-RN-05, E01-RN-07, E01-RN-09)", p -> {
                    item0(p).put("quantidade", -1);
                    return frete(p, "-5.00");
                }, "itens[0].quantidade:quantidade-invalida", "valor_frete:frete-negativo"),
                exemplo("#23 (E01-RN-09, E01-RN-10)", p -> frete(remover(p, "destinatario"), "-5.00"),
                        "destinatario:campo-obrigatorio"),
                exemplo("#24 (E01-RN-08)", p -> frete(p, "-1.555"), "valor_frete:casas-decimais-excedidas"),
                exemplo("#25 (E01-RN-08, E01-RN-07)", p -> {
                    item0(p).put("quantidade", "2");
                    return p;
                }, "itens[0].quantidade:formato-invalido"),
                exemplo("#26 (E01-RN-01, E01-RN-07)", p -> {
                    item0(p).remove("quantidade");
                    return p;
                }, "itens[0].quantidade:campo-obrigatorio"),
                exemplo("#27 (E01-RN-01, E01-RN-07)", p -> {
                    p.putArray("itens");
                    return p;
                }, "itens:campo-obrigatorio"),
                exemplo("#28 (E01-RN-08)", p -> p.put("data", "2022-13-45"), "data:formato-invalido"),
                exemplo("#29 (E01-RN-01, E01-RN-10)", p -> {
                    destinatario(p).remove("tipo_pessoa");
                    return p;
                }, "destinatario.tipo_pessoa:campo-obrigatorio"),
                exemplo("#30 (E01-RN-02)", p -> {
                    tipoPessoa(p, "JURIDICA").put("regime_tributacao", "SIMPLES_NACIONAL");
                    return p;
                }, "destinatario.documentos:documento-do-tipo-ausente"),
                exemplo("#31 (E01-RN-02)", p -> documento(pj(p, "SIMPLES_NACIONAL"), "CNPJ", "49.695.613/0001-81"),
                        "destinatario.documentos[0].numero:documento-invalido"),
                exemplo("#32 (E01-RN-02)", p -> documento(p, "CPF", "887 403 470 95")),
                exemplo("#33 (E01-RN-02)", p -> documento(p, "CPF", "111.111.111-11"),
                        "destinatario.documentos[0].numero:documento-invalido"),
                exemplo("#34 (E01-RN-04, E01-RN-07)", p -> {
                    item0(p).put("quantidade", 0);
                    return p;
                }, "itens[0].quantidade:quantidade-invalida"),
                exemplo("#35 (E01-RN-04, E01-RN-07)", p -> valorUnitario(p, "-50.00"),
                        "itens[0].valor_unitario:valor-unitario-invalido"),
                exemplo("#36 (E01-RN-01, E01-RN-10)", p -> p.putNull("valor_frete"), "valor_frete:campo-obrigatorio"),
                exemplo("#37 (E01-RN-10)", p -> p.put("observacao", "entregar pela manhã")));
    }

    /** Casos que a spec não trazia como exemplo, decididos na implementação da fase. */
    static Stream<Arguments> decisoesDaFase() {
        return Stream.of(
                exemplo("id_pedido com decimal (E01-RN-08)", p -> p.put("id_pedido", new BigDecimal("1.5")),
                        "id_pedido:formato-invalido"),
                exemplo("id_pedido como texto (E01-RN-08)", p -> p.put("id_pedido", "1"), "id_pedido:formato-invalido"),
                exemplo("data vazia (E01-RN-08)", p -> p.put("data", ""), "data:formato-invalido"),
                exemplo("data como número (E01-RN-08)", p -> p.put("data", 20220501), "data:formato-invalido"),
                exemplo("data como lista (E01-RN-08)", p -> {
                    p.putArray("data").add(2022).add(5).add(1);
                    return p;
                }, "data:formato-invalido"),
                exemplo("id_pedido vazio (E01-RN-08)", p -> p.put("id_pedido", ""), "id_pedido:formato-invalido"),
                exemplo("valor_frete vazio (E01-RN-08)", p -> p.put("valor_frete", ""), "valor_frete:formato-invalido"),
                exemplo("quantidade vazia (E01-RN-08)", p -> {
                    item0(p).put("quantidade", "");
                    return p;
                }, "itens[0].quantidade:formato-invalido"),
                exemplo("tipo de pessoa vazio (E01-RN-08)", p -> {
                    destinatario(p).put("tipo_pessoa", "");
                    return p;
                }, "destinatario.tipo_pessoa:valor-nao-aceito"),
                exemplo("id_pedido maior que o limite do Long (E01-RN-08)",
                        p -> p.put("id_pedido", new BigInteger("9223372036854775808")), "id_pedido:formato-invalido"),
                exemplo("quantidade 2.0 é inteira (E01-RN-04)", p -> {
                    item0(p).put("quantidade", new BigDecimal("2.0"));
                    return p;
                }),
                exemplo("zeros à direita não contam como casas (E01-RN-08)", p -> frete(p, "10.500")),
                exemplo("regime inválido sem tipo de pessoa: erro de tipo vem antes, sozinho (E01-RN-08, E01-RN-09)", p -> {
                    destinatario(regime(p, "MEI")).remove("tipo_pessoa");
                    return p;
                }, "destinatario.regime_tributacao:valor-nao-aceito"),
                exemplo("campo de texto com objeto (E01-RN-08)", p -> {
                    destinatario(p).putObject("nome");
                    return p;
                }, "destinatario.nome:formato-invalido"),
                exemplo("id_item numérico é aceito como no contrato atual (E01-RN-08)", p -> {
                    item0(p).put("id_item", 1);
                    return p;
                }),
                exemplo("finalidade inválida não confere endereço de entrega (E01-RN-06, E01-RN-10)",
                        p -> enderecos(p, endereco("RETIRADA", "SUDESTE")), "destinatario.enderecos[0].finalidade:valor-nao-aceito"),
                exemplo("tipo de documento inválido não confere coerência (E01-RN-02, E01-RN-10)",
                        p -> documento(p, "RG", "123456789"), "destinatario.documentos[0].tipo:valor-nao-aceito"),
                exemplo("tipo de pessoa inválido não confere regime nem coerência (E01-RN-10)", p -> {
                    tipoPessoa(p, "ESTRANGEIRA").put("regime_tributacao", "OUTROS");
                    return documento(p, "CNPJ", CNPJ);
                }, "destinatario.tipo_pessoa:valor-nao-aceito"));
    }

    /** Partes das regras sem exemplo na spec, apontadas pela revisão de rastreabilidade (T-10). */
    static Stream<Arguments> regrasSemExemplo() {
        return Stream.of(
                exemplo("documentos ausentes (E01-RN-01)", p -> {
                    destinatario(p).remove("documentos");
                    return p;
                }, "destinatario.documentos:campo-obrigatorio"),
                exemplo("documentos vazios (E01-RN-01)", p -> {
                    destinatario(p).putArray("documentos");
                    return p;
                }, "destinatario.documentos:campo-obrigatorio"),
                exemplo("endereços ausentes (E01-RN-01)", p -> {
                    destinatario(p).remove("enderecos");
                    return p;
                }, "destinatario.enderecos:campo-obrigatorio"),
                exemplo("endereços vazios (E01-RN-01)", p -> enderecos(p), "destinatario.enderecos:campo-obrigatorio"),
                exemplo("valor_total_itens ausente (E01-RN-01)", p -> remover(p, "valor_total_itens"),
                        "valor_total_itens:campo-obrigatorio"),
                exemplo("valor unitário ausente (E01-RN-01, E01-RN-07)", p -> {
                    item0(p).remove("valor_unitario");
                    return p;
                }, "itens[0].valor_unitario:campo-obrigatorio"),
                exemplo("documento sem tipo (E01-RN-01)", p -> {
                    ((ObjectNode) destinatario(p).get("documentos").get(0)).remove("tipo");
                    return p;
                }, "destinatario.documentos[0].tipo:campo-obrigatorio"),
                exemplo("documento sem número (E01-RN-01)", p -> {
                    ((ObjectNode) destinatario(p).get("documentos").get(0)).remove("numero");
                    return p;
                }, "destinatario.documentos[0].numero:campo-obrigatorio"),
                exemplo("endereço sem finalidade (E01-RN-01)", p -> {
                    ((ObjectNode) destinatario(p).get("enderecos").get(0)).remove("finalidade");
                    return p;
                }, "destinatario.enderecos[0].finalidade:campo-obrigatorio"),
                exemplo("PF com regime nulo (E01-RN-03, E01-RN-10)", p -> {
                    destinatario(p).putNull("regime_tributacao");
                    return p;
                }),
                exemplo("PF com CPF e CNPJ (E01-RN-02)", p -> {
                    ObjectNode cnpj = destinatario(p).withArray("documentos").addObject();
                    cnpj.put("tipo", "CNPJ");
                    cnpj.put("numero", CNPJ);
                    return p;
                }),
                exemplo("CNPJ com 13 dígitos (E01-RN-02)", p -> documento(pj(p, "SIMPLES_NACIONAL"), "CNPJ", "49.695.613/0001-8"),
                        "destinatario.documentos[0].numero:documento-invalido"),
                exemplo("valor unitário como texto (E01-RN-08, E01-RN-07)", p -> {
                    item0(p).put("valor_unitario", "50.00");
                    return p;
                }, "itens[0].valor_unitario:formato-invalido"),
                exemplo("valor_frete como texto (E01-RN-08)", p -> p.put("valor_frete", "10"), "valor_frete:formato-invalido"),
                exemplo("valor_total_itens como texto (E01-RN-08)", p -> p.put("valor_total_itens", "100.00"),
                        "valor_total_itens:formato-invalido"),
                exemplo("valor_total_itens com 3 casas (E01-RN-08)", p -> p.put("valor_total_itens", new BigDecimal("100.001")),
                        "valor_total_itens:casas-decimais-excedidas"),
                exemplo("sem tipo de pessoa, dígito verificador não conferido na etapa 1 (E01-RN-09, E01-RN-10)", p -> {
                    destinatario(documento(p, "CPF", "887.403.470-96")).remove("tipo_pessoa");
                    return p;
                }, "destinatario.tipo_pessoa:campo-obrigatorio"),
                exemplo("sem tipo de pessoa, coerência e regime não conferidos (E01-RN-10)", p -> {
                    destinatario(regime(documento(p, "CNPJ", CNPJ), "LUCRO_REAL")).remove("tipo_pessoa");
                    return p;
                }, "destinatario.tipo_pessoa:campo-obrigatorio"));
    }

    @ParameterizedTest(name = "E01 validação {0}")
    @MethodSource({"exemplosDeValidacao", "decisoesDaFase", "regrasSemExemplo"})
    void e01ExemplosDeValidacao(String exemplo, UnaryOperator<ObjectNode> mudanca, List<String> esperadas) {
        assertEquals(ordenadas(esperadas), violacoes(mudanca.apply(PedidoBase.novo())));
    }

    @Test
    void e01Validacao6_e01Rn07_recusaInformaTotalDeclaradoECalculado() {
        PedidoInvalidoException recusa = assertThrows(PedidoInvalidoException.class,
                () -> validar(itemUnico(PedidoBase.novo(), item("1", "5000.00", 1))));

        assertEquals("Total declarado 100.00, calculado 5000.00.", recusa.violacoes().get(0).detalhe());
    }

    @Test
    void e01Rn02_documentoInvalidoInformaOTipo() {
        PedidoInvalidoException recusa = assertThrows(PedidoInvalidoException.class,
                () -> validar(documento(PedidoBase.novo(), "CPF", "887.403.470-96")));

        assertEquals("CPF inválido.", recusa.violacoes().get(0).detalhe());
    }

    @Test
    void e01Rn02_cnpjInvalidoInformaOTipo() {
        PedidoInvalidoException recusa = assertThrows(PedidoInvalidoException.class,
                () -> validar(documento(pj(PedidoBase.novo(), "SIMPLES_NACIONAL"), "CNPJ", "49.695.613/0001-81")));

        assertEquals("CNPJ inválido.", recusa.violacoes().get(0).detalhe());
    }

    private List<String> violacoes(ObjectNode pedido) {
        PedidoRequest request;
        try {
            request = LEITOR.treeToValue(pedido, PedidoRequest.class);
        } catch (JacksonException e) {
            ViolacaoEntrada erroDeTipo = ViolacoesDeEntrada.deConversao(e).orElseThrow(() -> e);
            return List.of(erroDeTipo.campo() + ":" + erroDeTipo.motivo().codigo());
        }
        List<ViolacaoEntrada> entrada = ViolacoesDeEntrada.deAnotacoes(VALIDADOR.validate(request));
        if (!entrada.isEmpty()) {
            return ordenadas(entrada.stream()
                    .map(v -> v.campo() + ":" + v.motivo().codigo())
                    .collect(Collectors.toList()));
        }
        try {
            criarPedido(request);
            return List.of();
        } catch (PedidoInvalidoException e) {
            return ordenadas(e.violacoes().stream()
                    .map(v -> v.campo() + ":" + v.motivo().codigo())
                    .collect(Collectors.toList()));
        }
    }

    /** Pedido já válido na etapa 1: confere só as regras de negócio (etapa 2). */
    private void validar(ObjectNode pedido) {
        criarPedido(PedidoBase.converter(LEITOR, pedido, PedidoRequest.class));
    }

    private void criarPedido(PedidoRequest request) {
        GerarNotaFiscalCommand comando = pedidoMapper.paraComando(request);
        Pedido.criar(comando.idPedido(), comando.data(), comando.valorTotalItens(), comando.valorFrete(),
                comando.itens(), comando.destinatario());
    }

    private static JsonMapper leitorDaAplicacao() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().decimaisExatos().customize(builder);
        new JacksonConfig().entradaEstrita().customize(builder);
        return builder.build();
    }

    private static List<String> ordenadas(List<String> violacoes) {
        return violacoes.stream().sorted().collect(Collectors.toList());
    }

    private static Arguments exemplo(String nome, UnaryOperator<ObjectNode> mudanca, String... esperadas) {
        return Arguments.of(nome, mudanca, List.of(esperadas));
    }

    private static ObjectNode destinatario(ObjectNode pedido) {
        return (ObjectNode) pedido.get("destinatario");
    }

    private static ObjectNode item0(ObjectNode pedido) {
        return (ObjectNode) pedido.get("itens").get(0);
    }

    private static ObjectNode remover(ObjectNode pedido, String campo) {
        pedido.remove(campo);
        return pedido;
    }

    private static ObjectNode frete(ObjectNode pedido, String valor) {
        return PedidoBase.frete(pedido, valor);
    }

    private static ObjectNode itemUnico(ObjectNode pedido, ObjectNode item) {
        pedido.putArray("itens").add(item);
        return pedido;
    }

    private static ObjectNode valorUnitario(ObjectNode pedido, String valor) {
        item0(pedido).put("valor_unitario", new BigDecimal(valor));
        return pedido;
    }

    private static ObjectNode regime(ObjectNode pedido, String regime) {
        destinatario(pedido).put("regime_tributacao", regime);
        return pedido;
    }

    private static ObjectNode tipoPessoa(ObjectNode pedido, String tipo) {
        return destinatario(pedido).put("tipo_pessoa", tipo);
    }

    private static ObjectNode documento(ObjectNode pedido, String tipo, String numero) {
        ObjectNode documento = destinatario(pedido).putArray("documentos").addObject();
        documento.put("tipo", tipo);
        documento.put("numero", numero);
        return pedido;
    }
}
