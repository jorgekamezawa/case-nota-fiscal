package br.com.itau.geradornotafiscal.config;

import java.util.regex.Pattern;

/**
 * Regra única de mascaramento dos logs (F04-NF-05): CNPJ e CPF, com ou sem pontuação, viram asteriscos.
 */
public final class MascaraDadosPessoais {

    static final String MASCARA = "***";

    private static final Pattern CNPJ = Pattern.compile("(?<!\\d)\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}(?!\\d)");
    private static final Pattern CPF = Pattern.compile("(?<!\\d)\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}(?!\\d)");

    private MascaraDadosPessoais() {
    }

    public static String mascarar(String texto) {
        if (texto == null) {
            return null;
        }
        return CPF.matcher(CNPJ.matcher(texto).replaceAll(MASCARA)).replaceAll(MASCARA);
    }

    public static boolean precisaMascarar(String texto) {
        return texto != null && !texto.equals(mascarar(texto));
    }
}
