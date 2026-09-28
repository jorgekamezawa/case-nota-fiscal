package br.com.itau.geradornotafiscal.domain.service.tributacao;

import br.com.itau.geradornotafiscal.domain.valueobject.Item;
import br.com.itau.geradornotafiscal.domain.valueobject.ItemNotaFiscal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculadoraTributoTest {

    private static final int CHAMADAS_SIMULTANEAS = 150;

    private final CalculadoraTributo calculadora = new CalculadoraTributo();

    @Test
    @DisplayName("E01-RN-18: a segunda chamada devolve só os próprios itens")
    void e01Rn18_chamadasSeguidasNaoAcumulamItens() {
        calculadora.calcular(itens("primeiro", 1), BigDecimal.ZERO);

        List<ItemNotaFiscal> resultado = calculadora.calcular(itens("segundo", 1), BigDecimal.ZERO);

        assertEquals(List.of("segundo-0"), ids(resultado));
    }

    @Test
    @DisplayName("E01-RN-18, E01-NF-04: 150 chamadas simultâneas, cada uma só com os próprios itens")
    void e01Rn18_e01Nf04_chamadasSimultaneasNaoMisturamItens() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CHAMADAS_SIMULTANEAS);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<List<String>>> resultados = new ArrayList<>();
        try {
            for (int chamada = 0; chamada < CHAMADAS_SIMULTANEAS; chamada++) {
                List<Item> itens = itens("pedido" + chamada, 3);
                resultados.add(executor.submit(() -> {
                    largada.await();
                    return ids(calculadora.calcular(itens, BigDecimal.ZERO));
                }));
            }
            largada.countDown();

            for (int chamada = 0; chamada < CHAMADAS_SIMULTANEAS; chamada++) {
                assertEquals(idsEsperados("pedido" + chamada, 3), resultados.get(chamada).get(10, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static List<Item> itens(String prefixo, int quantidade) {
        return idsEsperados(prefixo, quantidade).stream()
                .map(id -> new Item(id, "item", BigDecimal.TEN, BigDecimal.ONE))
                .collect(Collectors.toList());
    }

    private static List<String> idsEsperados(String prefixo, int quantidade) {
        return IntStream.range(0, quantidade)
                .mapToObj(i -> prefixo + "-" + i)
                .collect(Collectors.toList());
    }

    private static List<String> ids(List<ItemNotaFiscal> itens) {
        return itens.stream().map(ItemNotaFiscal::idItem).collect(Collectors.toList());
    }
}
