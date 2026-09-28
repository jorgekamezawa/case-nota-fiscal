package br.com.itau.geradornotafiscal.adapter.in.web.validacao;

import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.DestinatarioRequest;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.DocumentoRequest;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.EnderecoRequest;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.ItemRequest;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.PedidoRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.metadata.BeanDescriptor;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.PropertyDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViolacoesDeEntradaTest {

    private static final Validator VALIDADOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("E01-NF-03: toda anotação de validação dos DTOs de entrada tem motivo de recusa documentado")
    void e01Nf03_todaAnotacaoDeEntradaTemMotivo() {
        List<String> semMotivo = Stream.of(PedidoRequest.class, ItemRequest.class, DestinatarioRequest.class,
                        DocumentoRequest.class, EnderecoRequest.class)
                .map(VALIDADOR::getConstraintsForClass)
                .flatMap(ViolacoesDeEntradaTest::restricoes)
                .map(restricao -> restricao.getAnnotation().annotationType())
                .filter(anotacao -> !ViolacoesDeEntrada.mapeada(anotacao))
                .map(Class::getName)
                .distinct()
                .toList();

        assertEquals(List.of(), semMotivo);
    }

    private static Stream<ConstraintDescriptor<?>> restricoes(BeanDescriptor classe) {
        return Stream.concat(classe.getConstraintDescriptors().stream(),
                classe.getConstrainedProperties().stream().flatMap(ViolacoesDeEntradaTest::restricoesDoCampo));
    }

    private static Stream<ConstraintDescriptor<?>> restricoesDoCampo(PropertyDescriptor campo) {
        return Stream.concat(campo.getConstraintDescriptors().stream(),
                campo.getConstrainedContainerElementTypes().stream().flatMap(elemento -> elemento.getConstraintDescriptors().stream()));
    }
}
