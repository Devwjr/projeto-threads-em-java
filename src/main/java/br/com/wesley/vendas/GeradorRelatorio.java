package br.com.wesley.vendas;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class GeradorRelatorio {
    public void gerar(Path saida, List<ResultadoArquivo> resultados,
                      ResultadoArquivo total, List<ErroProcessamento> falhas) throws IOException {
        Files.createDirectories(saida);
        List<ResultadoArquivo> lojas = resultados.stream()
            .sorted(Comparator.comparing(ResultadoArquivo::nomeArquivo)).toList();
        escrever(saida.resolve("resumo-por-loja.csv"), escritor -> {
            linha(escritor, "loja", "vendas_validas", "unidades", "faturamento");
            for (ResultadoArquivo loja : lojas) {
                String nome = loja.nomeArquivo();
                linha(escritor, nome.substring(0, nome.length() - 4),
                    loja.vendasValidas(), loja.unidades(), dinheiro(loja.faturamento()));
            }
        });
        escrever(saida.resolve("resumo-por-produto.csv"), escritor -> {
            linha(escritor, "produto", "unidades", "faturamento");
            for (Map.Entry<String, TotalProduto> item : new TreeMap<>(total.produtos()).entrySet()) {
                linha(escritor, item.getKey(), item.getValue().unidades(),
                    dinheiro(item.getValue().faturamento()));
            }
        });
        List<ErroProcessamento> erros = new ArrayList<>(total.erros());
        erros.addAll(falhas);
        erros.sort(Comparator.comparing(ErroProcessamento::arquivo)
            .thenComparing(ErroProcessamento::linha, Comparator.nullsFirst(Comparator.naturalOrder())));
        escrever(saida.resolve("erros.csv"), escritor -> {
            linha(escritor, "arquivo", "linha", "motivo");
            for (ErroProcessamento erro : erros) {
                linha(escritor, erro.arquivo(), erro.linha(), erro.motivo());
            }
        });
    }

    private static String dinheiro(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static void linha(BufferedWriter escritor, Object... campos) throws IOException {
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) escritor.write(',');
            String texto = campos[i] == null ? "" : campos[i].toString();
            if (texto.contains(",") || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
                escritor.write("\"" + texto.replace("\"", "\"\"") + "\"");
            } else {
                escritor.write(texto);
            }
        }
        escritor.newLine();
    }

    // Cada arquivo é publicado depois de sua escrita completa.
    private static void escrever(Path destino, Escrita escrita) throws IOException {
        Path temporario = Files.createTempFile(destino.getParent(), ".relatorio-", ".tmp");
        try {
            try (BufferedWriter escritor = Files.newBufferedWriter(temporario, StandardCharsets.UTF_8)) {
                escrita.executar(escritor);
            }
            try {
                Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException erro) {
                Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporario);
        }
    }

    @FunctionalInterface
    private interface Escrita {
        void executar(BufferedWriter escritor) throws IOException;
    }
}
