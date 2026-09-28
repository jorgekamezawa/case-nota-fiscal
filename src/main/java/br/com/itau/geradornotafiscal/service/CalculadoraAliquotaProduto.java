package br.com.itau.geradornotafiscal.service;

import br.com.itau.geradornotafiscal.model.Item;
import br.com.itau.geradornotafiscal.model.ItemNotaFiscal;
import br.com.itau.geradornotafiscal.service.calculo.Arredondamento;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class CalculadoraAliquotaProduto {

    public List<ItemNotaFiscal> calcularAliquota(List<Item> items, BigDecimal aliquotaPercentual) {
        List<ItemNotaFiscal> itemNotaFiscalList = new ArrayList<>();

        for (Item item : items) {
            BigDecimal valorTributo = Arredondamento.duasCasas(item.getValorUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())).multiply(aliquotaPercentual));
            ItemNotaFiscal itemNotaFiscal = ItemNotaFiscal.builder()
                    .idItem(item.getIdItem())
                    .descricao(item.getDescricao())
                    .valorUnitario(Arredondamento.duasCasas(item.getValorUnitario()))
                    .quantidade(item.getQuantidade())
                    .valorTributoItem(valorTributo)
                    .build();
            itemNotaFiscalList.add(itemNotaFiscal);
        }
        return itemNotaFiscalList;
    }
}



