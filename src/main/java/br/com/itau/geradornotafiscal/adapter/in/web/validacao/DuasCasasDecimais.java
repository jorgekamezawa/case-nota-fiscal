package br.com.itau.geradornotafiscal.adapter.in.web.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;

/**
 * Valor monetário com no máximo 2 casas decimais; zeros à direita não contam (10.500 tem 2 casas) (E01-RN-08).
 */
@Documented
@Constraint(validatedBy = DuasCasasDecimais.Validador.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DuasCasasDecimais {

    String message() default "Valor com mais de 2 casas decimais.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<DuasCasasDecimais, BigDecimal> {

        @Override
        public boolean isValid(BigDecimal valor, ConstraintValidatorContext contexto) {
            return valor == null || valor.stripTrailingZeros().scale() <= 2;
        }
    }
}
