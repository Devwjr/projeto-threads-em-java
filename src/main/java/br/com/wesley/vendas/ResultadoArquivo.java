package br.com.wesley.vendas;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ResultadoArquivo(String nomeArquivo, BigDecimal faturamento,
                               long vendasValidas, long unidades,
                               Map<String, TotalProduto> produtos,
                               List<ErroProcessamento> erros) {
    public ResultadoArquivo {
        produtos = Map.copyOf(produtos);
        erros = List.copyOf(erros);
    }

    public long linhasRejeitadas() {
        return erros.size();
    }
}
