package br.com.itau.geradornotafiscal.adapter.in.web.validacao;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Data só como texto AAAA-MM-DD e existente no calendário; número, lista ou texto vazio são erro de formato (E01-RN-08).
 */
public class DataNoFormatoIso extends StdDeserializer<LocalDate> {

    public DataNoFormatoIso() {
        super(LocalDate.class);
    }

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext contexto) {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (LocalDate) contexto.handleUnexpectedToken(LocalDate.class, parser);
        }
        String texto = parser.getString();
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            return (LocalDate) contexto.handleWeirdStringValue(LocalDate.class, texto, "Data fora do formato AAAA-MM-DD");
        }
    }
}
