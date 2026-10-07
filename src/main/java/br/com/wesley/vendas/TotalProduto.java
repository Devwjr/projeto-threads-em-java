package br.com.wesley.vendas;

import java.math.BigDecimal;

public record TotalProduto(long unidades, BigDecimal faturamento) {
    public TotalProduto somar(TotalProduto outro) {
        return new TotalProduto(Math.addExact(unidades, outro.unidades),
            faturamento.add(outro.faturamento));
    }
}
