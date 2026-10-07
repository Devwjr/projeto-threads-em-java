package br.com.wesley.vendas;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcessadorArquivo {
    public ResultadoArquivo processar(Path arquivo) throws IOException, InterruptedException {
        verificarInterrupcao();
        String nome = arquivo.getFileName().toString();
        BigDecimal faturamento = BigDecimal.ZERO;
        long vendas = 0;
        long unidades = 0;
        Map<String, TotalProduto> produtos = new HashMap<>();
        List<ErroProcessamento> erros = new ArrayList<>();

        try (BufferedReader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            if (!"id_venda,data,produto,quantidade,preco_unitario".equals(leitor.readLine())) {
                throw new IllegalArgumentException("Cabeçalho inválido.");
            }
            String linha;
            long numeroLinha = 1;
            while ((linha = leitor.readLine()) != null) {
                verificarInterrupcao();
                numeroLinha++;
                Venda venda;
                try {
                    venda = Venda.interpretar(linha);
                } catch (IllegalArgumentException | DateTimeException erro) {
                    erros.add(new ErroProcessamento(nome, numeroLinha, erro.getMessage()));
                    continue;
                }
                vendas = Math.incrementExact(vendas);
                unidades = Math.addExact(unidades, venda.quantidade());
                faturamento = faturamento.add(venda.valor());
                produtos.merge(venda.produto(),
                    new TotalProduto(venda.quantidade(), venda.valor()), TotalProduto::somar);
            }
        }
        verificarInterrupcao();
        return new ResultadoArquivo(nome, faturamento, vendas, unidades, produtos, erros);
    }

    private static void verificarInterrupcao() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Processamento cancelado.");
        }
    }
}
