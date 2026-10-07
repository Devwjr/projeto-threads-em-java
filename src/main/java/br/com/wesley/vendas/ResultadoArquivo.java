package br.com.wesley.vendas;

import java.math.BigDecimal;

public record ResultadoArquivo(
    String nomeArquivo,
    BigDecimal faturamento,
    int vendasValidas,
    int linhasRejeitadas
) {}
