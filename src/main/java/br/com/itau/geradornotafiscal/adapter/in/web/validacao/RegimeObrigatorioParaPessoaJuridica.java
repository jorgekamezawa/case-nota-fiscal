package br.com.itau.geradornotafiscal.adapter.in.web.validacao;

import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.DestinatarioRequest;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoPessoa;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Regime de tributação obrigatório para pessoa jurídica (E01-RN-01). Sem tipo de pessoa, não é conferido (E01-RN-10).
 */
@Documented
@Constraint(validatedBy = RegimeObrigatorioParaPessoaJuridica.Validador.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegimeObrigatorioParaPessoaJuridica {

    String message() default "Campo obrigatório.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<RegimeObrigatorioParaPessoaJuridica, DestinatarioRequest> {

        @Override
        public boolean isValid(DestinatarioRequest destinatario, ConstraintValidatorContext contexto) {
            if (destinatario == null || destinatario.tipoPessoa() != TipoPessoa.JURIDICA || destinatario.regimeTributacao() != null) {
                return true;
            }
            contexto.disableDefaultConstraintViolation();
            contexto.buildConstraintViolationWithTemplate(contexto.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("regimeTributacao")
                    .addConstraintViolation();
            return false;
        }
    }
}
