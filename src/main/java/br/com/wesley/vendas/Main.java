package br.com.wesley.vendas;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length == 1 && "--help".equals(args[0])) {
            System.out.println("Uso: java ... Main --entrada dados/entrada --saida dados/saida --threads 4");
            return;
        }
        long inicio = System.nanoTime();
        try {
            executar(Configuracao.interpretar(args), inicio);
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            System.err.println("Processamento interrompido; relatórios não foram gerados.");
            System.exit(1);
        } catch (IOException | UncheckedIOException | IllegalArgumentException
                 | IllegalStateException | ArithmeticException erro) {
            System.err.println("Erro: " + erro.getMessage());
            System.exit(1);
        }
    }

    private static void executar(Configuracao config, long inicio)
            throws IOException, InterruptedException {
        List<Path> arquivos;
        try (Stream<Path> caminhos = Files.list(config.entrada())) {
            arquivos = caminhos.filter(Files::isRegularFile)
                .filter(caminho -> caminho.getFileName().toString().endsWith(".csv"))
                .sorted().toList();
        }
        List<ResultadoArquivo> resultados = new ArrayList<>();
        List<ErroProcessamento> falhas = new ArrayList<>();
        if (!arquivos.isEmpty()) {
            processar(arquivos, config.threads(), resultados, falhas);
        }
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        ResultadoArquivo total = new Consolidador().consolidar(resultados);
        new GeradorRelatorio().gerar(config.saida(), resultados, total, falhas);
        System.out.println("Arquivos concluídos: " + resultados.size());
        System.out.println("Arquivos com falha: " + falhas.size());
        System.out.println("Vendas válidas: " + total.vendasValidas());
        System.out.println("Linhas rejeitadas: " + total.linhasRejeitadas());
        System.out.println("Unidades vendidas: " + total.unidades());
        System.out.println("Faturamento total: " + total.faturamento().setScale(2).toPlainString());
        System.out.printf("Duração total: %.3f s%n", (System.nanoTime() - inicio) / 1_000_000_000.0);
        System.out.println("Relatórios: " + config.saida().toAbsolutePath());
    }

    private static void processar(List<Path> arquivos, int threads,
                                 List<ResultadoArquivo> resultados,
                                 List<ErroProcessamento> falhas) throws InterruptedException {
        int trabalhadores = Math.min(threads, arquivos.size());
        ExecutorService executor = Executors.newFixedThreadPool(trabalhadores);
        ExecutorCompletionService<Conclusao> conclusoes = new ExecutorCompletionService<>(executor);
        ProcessadorArquivo processador = new ProcessadorArquivo();
        boolean concluido = false;
        try {
            // Mantém apenas uma tarefa por trabalhador em andamento, sem fila crescente.
            int enviados = 0;
            for (; enviados < trabalhadores; enviados++) {
                submeter(conclusoes, processador, arquivos.get(enviados));
            }
            for (int recebidos = 0; recebidos < arquivos.size(); recebidos++) {
                Conclusao conclusao;
                try {
                    conclusao = conclusoes.take().get();
                } catch (ExecutionException erro) {
                    throw new IllegalStateException("Falha inesperada em um trabalhador.", erro.getCause());
                }
                if (conclusao.erro() != null) {
                    falhas.add(conclusao.erro());
                    System.err.println("Falha em " + conclusao.erro().arquivo() + ": " + conclusao.erro().motivo());
                } else {
                    resultados.add(conclusao.resultado());
                    System.out.println("Concluído: " + conclusao.resultado().nomeArquivo());
                }
                if (enviados < arquivos.size()) {
                    submeter(conclusoes, processador, arquivos.get(enviados++));
                }
            }
            concluido = true;
        } finally {
            if (concluido) executor.shutdown();
            else executor.shutdownNow();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Os trabalhadores não encerraram.");
                    }
                }
            } catch (InterruptedException erro) {
                executor.shutdownNow();
                throw erro;
            }
        }
    }

    private static void submeter(ExecutorCompletionService<Conclusao> conclusoes,
                                 ProcessadorArquivo processador, Path arquivo) {
        conclusoes.submit(() -> {
            try {
                return new Conclusao(processador.processar(arquivo), null);
            } catch (IOException | IllegalArgumentException | ArithmeticException erro) {
                return new Conclusao(null, new ErroProcessamento(
                    arquivo.getFileName().toString(), null, erro.getMessage()));
            }
        });
    }

    private record Conclusao(ResultadoArquivo resultado, ErroProcessamento erro) {}
}
