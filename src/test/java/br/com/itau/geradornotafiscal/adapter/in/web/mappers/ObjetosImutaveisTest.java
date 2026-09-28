package br.com.itau.geradornotafiscal.adapter.in.web.mappers;

import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.ItemRequest;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.request.PedidoRequest;
import br.com.itau.geradornotafiscal.adapter.in.web.dto.response.NotaFiscalResponse;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import br.com.itau.geradornotafiscal.domain.entity.Pedido;
import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import br.com.itau.geradornotafiscal.domain.valueobject.Documento;
import br.com.itau.geradornotafiscal.domain.valueobject.Endereco;
import br.com.itau.geradornotafiscal.domain.valueobject.Finalidade;
import br.com.itau.geradornotafiscal.domain.valueobject.Item;
import br.com.itau.geradornotafiscal.domain.valueobject.ItemNotaFiscal;
import br.com.itau.geradornotafiscal.domain.valueobject.Regiao;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoDocumento;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoPessoa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjetosImutaveisTest {

    @Test
    @DisplayName("F03-NF-06: contrato de entrada guarda cópia das listas, que não muda")
    void f03Nf06_contratoDeEntradaImutavel() {
        List<ItemRequest> itens = new ArrayList<>(List.of(new ItemRequest("1", "Teclado", BigDecimal.TEN, BigDecimal.ONE)));
        PedidoRequest pedido = new PedidoRequest(1L, null, BigDecimal.TEN, BigDecimal.ONE, itens, null);

        itens.clear();

        assertEquals(1, pedido.itens().size());
        assertThrows(UnsupportedOperationException.class, () -> pedido.itens().clear());
    }

    @Test
    @DisplayName("F03-NF-06: domínio e contrato de saída não mudam depois de criados")
    void f03Nf06_dominioEContratoDeSaidaImutaveis() {
        Destinatario destinatario = new Destinatario("Fulano", TipoPessoa.FISICA, null,
                List.of(new Documento("887.403.470-95", TipoDocumento.CPF)),
                List.of(new Endereco(null, null, null, null, null, null, null, null, Finalidade.ENTREGA, Regiao.SUDESTE)));
        Pedido pedido = Pedido.criar(1L, null, new BigDecimal("10.00"), BigDecimal.ONE,
                new ArrayList<>(List.of(new Item("1", "Teclado", BigDecimal.TEN, BigDecimal.ONE))), destinatario);
        NotaFiscal nota = NotaFiscal.emitir(pedido,
                List.of(new ItemNotaFiscal("1", "Teclado", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO)),
                BigDecimal.ONE, LocalDateTime.now());
        NotaFiscalResponse resposta = new NotaFiscalMapper().paraResponse(nota);

        assertThrows(UnsupportedOperationException.class, () -> pedido.getItens().clear());
        assertThrows(UnsupportedOperationException.class, () -> nota.getItens().clear());
        assertThrows(UnsupportedOperationException.class, () -> resposta.itens().clear());
        assertThrows(UnsupportedOperationException.class, () -> resposta.destinatario().documentos().clear());
    }
}
