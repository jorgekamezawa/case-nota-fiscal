package br.com.itau.geradornotafiscal.domain.valueobject;

// Número como recebido, sem a limpeza da E01-RN-02: a nota devolve o documento como enviado (E01-RN-17).
public record Documento(String numero, TipoDocumento tipo) {
}
