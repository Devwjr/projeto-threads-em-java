package br.com.wesley.vendas;

import java.math.BigDecimal;
import java.util.List;

public class Consolidador {

    public ResultadoArquivo consolidar(List<ResultadoArquivo> resultados) {
        BigDecimal faturamento = BigDecimal.ZERO;
        int vendasValidas = 0;
        int linhasRejeitadas = 0;

        for (ResultadoArquivo resultado : resultados) {
            faturamento = faturamento.add(resultado.faturamento());
            vendasValidas += resultado.vendasValidas();
            linhasRejeitadas += resultado.linhasRejeitadas();
        }

        return new ResultadoArquivo(
            "Total geral", faturamento, vendasValidas, linhasRejeitadas
        );
    }
}
