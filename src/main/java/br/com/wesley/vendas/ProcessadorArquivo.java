package br.com.wesley.vendas;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;


public class ProcessadorArquivo {

    public ResultadoArquivo processar(Path arquivo) throws IOException {
        System.out.println(
            "Processando " + arquivo.getFileName()
                + " na thread " + Thread.currentThread().getName()
        );
        BigDecimal faturamento = BigDecimal.ZERO;
        int vendasValidas = 0;
        int linhasRejeitadas = 0;

        try (BufferedReader leitor = Files.newBufferedReader(
                arquivo, StandardCharsets.UTF_8)) {

            String cabecalho = leitor.readLine();

            if (!"id_venda,data,produto,quantidade,preco_unitario"
                    .equals(cabecalho)) {
                throw new IllegalArgumentException("Cabeçalho inválido.");
            }

            String linha;
            int numeroLinha = 1;
            while ((linha = leitor.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Processamento cancelado.");
                }
                numeroLinha++;

                try {
                    String[] campos = linha.split(",", -1);

                    if (campos.length != 5) {
                        throw new IllegalArgumentException(
                            "Esperados 5 campos."
                        );
                    }

                    for (int i = 0; i < campos.length; i++) {
                        campos[i] = campos[i].trim();

                        if (campos[i].isEmpty()) {
                            throw new IllegalArgumentException(
                                "Campo " + (i + 1) + " está vazio."
                            );
                        }
                    }

                    if (!campos[1].matches("\\d{4}-\\d{2}-\\d{2}")) {
                        throw new IllegalArgumentException(
                            "A data deve estar no formato AAAA-MM-DD."
                        );
                    }

                    LocalDate.parse(campos[1]);

                    int quantidade = Integer.parseInt(campos[3]);

                    if (quantidade <= 0) {
                        throw new IllegalArgumentException(
                            "A quantidade deve ser maior que zero."
                        );
                    }

                    if (!campos[4].matches("\\d+(\\.\\d{1,2})?")) {
                        throw new IllegalArgumentException(
                            "Preço inválido: use ponto e até duas casas decimais."
                        );
                    }

                    BigDecimal precoUnitario = new BigDecimal(campos[4]);

                    if (precoUnitario.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException(
                            "O preço deve ser maior que zero."
                        );
                    }

                    BigDecimal valorVenda = precoUnitario.multiply(
                        BigDecimal.valueOf(quantidade)
                    );

                    faturamento = faturamento.add(valorVenda);
                    vendasValidas++;

                } catch (IllegalArgumentException | DateTimeException erro) {
                    linhasRejeitadas++;

                    System.err.println(
                        arquivo.getFileName()
                            + " — linha " + numeroLinha
                            + " rejeitada: " + erro.getMessage()
                    );
                }
            }
        }

        return new ResultadoArquivo(
            arquivo.getFileName().toString(),
            faturamento,
            vendasValidas,
            linhasRejeitadas
        );
    }
}
