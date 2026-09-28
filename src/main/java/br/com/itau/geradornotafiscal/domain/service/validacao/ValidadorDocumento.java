package br.com.itau.geradornotafiscal.domain.service.validacao;

import br.com.itau.geradornotafiscal.domain.valueobject.TipoDocumento;

/**
 * CPF e CNPJ: limpeza dos caracteres que não são dígitos, tamanho, dígitos iguais e dígito verificador (E01-RN-02).
 */
public final class ValidadorDocumento {

    private static final int[] PESOS_CNPJ = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private ValidadorDocumento() {
    }

    public static boolean valido(TipoDocumento tipo, String numero) {
        String digitos = numero.replaceAll("\\D", "");
        int tamanho = tipo == TipoDocumento.CPF ? 11 : 14;
        if (digitos.length() != tamanho || digitos.chars().distinct().count() == 1) {
            return false;
        }
        return tipo == TipoDocumento.CPF ? cpfValido(digitos) : cnpjValido(digitos);
    }

    private static boolean cpfValido(String cpf) {
        return digitoCpf(cpf, 9) == cpf.charAt(9) - '0' && digitoCpf(cpf, 10) == cpf.charAt(10) - '0';
    }

    private static int digitoCpf(String cpf, int posicoes) {
        int soma = 0;
        for (int i = 0; i < posicoes; i++) {
            soma += (cpf.charAt(i) - '0') * (posicoes + 1 - i);
        }
        int resto = soma * 10 % 11;
        return resto == 10 ? 0 : resto;
    }

    private static boolean cnpjValido(String cnpj) {
        return digitoCnpj(cnpj, 12) == cnpj.charAt(12) - '0' && digitoCnpj(cnpj, 13) == cnpj.charAt(13) - '0';
    }

    private static int digitoCnpj(String cnpj, int posicoes) {
        int soma = 0;
        for (int i = 0; i < posicoes; i++) {
            soma += (cnpj.charAt(i) - '0') * PESOS_CNPJ[PESOS_CNPJ.length - posicoes + i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
