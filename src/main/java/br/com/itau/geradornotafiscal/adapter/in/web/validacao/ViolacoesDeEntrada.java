package br.com.itau.geradornotafiscal.adapter.in.web.validacao;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Converte a etapa 1 da validação (E01-RN-01, E01-RN-08) em campos da recusa, com o caminho do contrato
 * (ex.: {@code itens[0].valor_unitario}) e o motivo.
 */
public final class ViolacoesDeEntrada {

    private static final Map<Class<? extends Annotation>, MotivoEntrada> MOTIVO_POR_ANOTACAO = Map.of(
            NotNull.class, MotivoEntrada.CAMPO_OBRIGATORIO,
            NotEmpty.class, MotivoEntrada.CAMPO_OBRIGATORIO,
            RegimeObrigatorioParaPessoaJuridica.class, MotivoEntrada.CAMPO_OBRIGATORIO,
            DuasCasasDecimais.class, MotivoEntrada.CASAS_DECIMAIS_EXCEDIDAS);

    private ViolacoesDeEntrada() {
    }

    /** Violações das anotações dos DTOs de entrada, ordenadas pelo campo. */
    public static List<ViolacaoEntrada> deAnotacoes(Collection<? extends ConstraintViolation<?>> violacoes) {
        return violacoes.stream()
                .map(ViolacoesDeEntrada::deAnotacao)
                .distinct()
                .sorted(Comparator.comparing(ViolacaoEntrada::campo))
                .toList();
    }

    /**
     * Erro de tipo na conversão do JSON, um por vez (E01-RN-09). Vazio quando o corpo não é JSON válido nem objeto,
     * caso respondido como corpo inválido.
     */
    public static Optional<ViolacaoEntrada> deConversao(JacksonException erro) {
        boolean deCampo = erro instanceof DatabindException || erro instanceof InputCoercionException;
        if (!deCampo || erro.getPath().isEmpty()) {
            return Optional.empty();
        }
        String campo = caminho(erro.getPath());
        if (erro instanceof InvalidFormatException formato && formato.getValue() instanceof String
                && formato.getTargetType() != null && formato.getTargetType().isEnum()) {
            return Optional.of(new ViolacaoEntrada(campo, MotivoEntrada.VALOR_NAO_ACEITO,
                    "Valor fora dos aceitos: " + aceitos(formato.getTargetType()) + "."));
        }
        if (erro instanceof MismatchedInputException || erro instanceof InputCoercionException) {
            return Optional.of(new ViolacaoEntrada(campo, MotivoEntrada.FORMATO_INVALIDO));
        }
        return Optional.empty();
    }

    private static ViolacaoEntrada deAnotacao(ConstraintViolation<?> violacao) {
        Class<? extends Annotation> anotacao = violacao.getConstraintDescriptor().getAnnotation().annotationType();
        MotivoEntrada motivo = MOTIVO_POR_ANOTACAO.get(anotacao);
        if (motivo == null) {
            throw new IllegalStateException("Anotação de validação sem motivo de recusa mapeado: " + anotacao.getName());
        }
        String detalhe = anotacao == NotEmpty.class ? "Informe ao menos 1 elemento." : motivo.mensagem();
        return new ViolacaoEntrada(emSnakeCase(violacao.getPropertyPath().toString()), motivo, detalhe);
    }

    /** Se a anotação tem motivo de recusa; garante que toda anotação usada nos DTOs de entrada vira um type documentado. */
    static boolean mapeada(Class<? extends Annotation> anotacao) {
        return MOTIVO_POR_ANOTACAO.containsKey(anotacao);
    }

    // O caminho da anotação usa os nomes Java (valorUnitario); o contrato usa snake_case (valor_unitario).
    private static String emSnakeCase(String caminho) {
        return caminho.replace(".<list element>", "")
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase();
    }

    private static String caminho(List<JacksonException.Reference> referencias) {
        StringBuilder caminho = new StringBuilder();
        for (JacksonException.Reference referencia : referencias) {
            if (referencia.getPropertyName() != null) {
                caminho.append(caminho.length() == 0 ? "" : ".").append(referencia.getPropertyName());
            } else if (referencia.getIndex() >= 0) {
                caminho.append('[').append(referencia.getIndex()).append(']');
            }
        }
        return caminho.toString();
    }

    private static String aceitos(Class<?> tipo) {
        return Arrays.stream(tipo.getEnumConstants()).map(Object::toString).collect(Collectors.joining(", "));
    }
}
