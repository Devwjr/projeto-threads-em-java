package br.com.wesley.vendas;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) {
        Path pastaEntrada = Path.of("dados/entrada");
        ProcessadorArquivo processador = new ProcessadorArquivo();
        List<ResultadoArquivo> resultados = new ArrayList<>();
        int arquivosComFalha = 0;

        try (Stream<Path> caminhos = Files.list(pastaEntrada)) {
            List<Path> arquivos = caminhos
                .filter(Files::isRegularFile)
                .filter(caminho -> caminho.getFileName().toString().endsWith(".csv"))
                .sorted()
                .toList();

            ExecutorService executor = Executors.newFixedThreadPool(4);

            try {
                List<Future<ResultadoArquivo>> tarefas = new ArrayList<>();

                // Submete todos os arquivos antes de aguardar os resultados.
                for (Path arquivo : arquivos) {
                    tarefas.add(executor.submit(() -> processador.processar(arquivo)));
                }

                // Apenas a thread principal altera a lista de resultados.
                for (int i = 0; i < tarefas.size(); i++) {
                    try {
                        ResultadoArquivo resultado = tarefas.get(i).get();
                        resultados.add(resultado);
                        mostrarResultado(resultado);
                    } catch (ExecutionException erro) {
                        arquivosComFalha++;
                        System.err.println(
                            "Erro ao processar " + arquivos.get(i)
                                + ": " + erro.getCause().getMessage()
                        );
                    } catch (InterruptedException erro) {
                        for (Future<ResultadoArquivo> tarefa : tarefas) {
                            tarefa.cancel(true);
                        }
                        executor.shutdownNow();
                        Thread.currentThread().interrupt();
                        System.err.println("Processamento interrompido.");
                        return;
                    }
                }
            } finally {
                executor.shutdown();
            }

        } catch (IOException | UncheckedIOException erro) {
            System.err.println(
                "Erro ao listar " + pastaEntrada + ": " + erro.getMessage()
            );
            System.exit(1);
            return;
        }

        ResultadoArquivo total = new Consolidador().consolidar(resultados);
        mostrarResultado(total);
        System.out.println("Arquivos concluídos: " + resultados.size());
        System.out.println("Arquivos com falha: " + arquivosComFalha);
    }

    private static void mostrarResultado(ResultadoArquivo resultado) {
        System.out.println("Arquivo: " + resultado.nomeArquivo());
        System.out.println("Vendas válidas: " + resultado.vendasValidas());
        System.out.println("Linhas rejeitadas: " + resultado.linhasRejeitadas());
        System.out.println(
            "Faturamento total: " + resultado.faturamento().toPlainString()
        );
        System.out.println();
    }
}
