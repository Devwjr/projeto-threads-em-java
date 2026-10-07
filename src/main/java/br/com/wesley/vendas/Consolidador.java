package br.com.wesley.vendas;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Consolidador {
    public ResultadoArquivo consolidar(List<ResultadoArquivo> resultados) {
        BigDecimal faturamento = BigDecimal.ZERO;
        long vendas = 0;
        long unidades = 0;
        Map<String, TotalProduto> produtos = new TreeMap<>();
        List<ErroProcessamento> erros = new ArrayList<>();
        for (ResultadoArquivo resultado : resultados) {
            faturamento = faturamento.add(resultado.faturamento());
            vendas = Math.addExact(vendas, resultado.vendasValidas());
            unidades = Math.addExact(unidades, resultado.unidades());
            resultado.produtos().forEach((produto, total) ->
                produtos.merge(produto, total, TotalProduto::somar));
            erros.addAll(resultado.erros());
        }
        return new ResultadoArquivo("Total geral", faturamento, vendas, unidades, produtos, erros);
    }
}
