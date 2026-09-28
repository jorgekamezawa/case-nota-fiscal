package br.com.itau.geradornotafiscal.domain.service.tributacao;

import br.com.itau.geradornotafiscal.domain.service.calculo.Arredondamento;
import br.com.itau.geradornotafiscal.domain.valueobject.Item;
import br.com.itau.geradornotafiscal.domain.valueobject.ItemNotaFiscal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tributo de cada item: valor unitário × quantidade × alíquota, arredondado a 2 casas (E01-RN-14, E01-RN-16).
 */
@Component
public class CalculadoraTributo {

    public List<ItemNotaFiscal> calcular(List<Item> itens, BigDecimal aliquota) {
        return itens.stream()
                .map(item -> new ItemNotaFiscal(
                        item.idItem(),
                        item.descricao(),
                        Arredondamento.duasCasas(item.valorUnitario()),
                        item.quantidade(),
                        Arredondamento.duasCasas(item.valorUnitario().multiply(item.quantidade()).multiply(aliquota))))
                .collect(Collectors.toList());
    }
}
