package br.com.wesley.vendas;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Gera uma massa temporária e compara execuções completas em novas JVMs. */
public class Desempenho {
    public static void main(String[] args) {
        if (args.length == 1 && "--help".equals(args[0])) {
            System.out.println("Opções: --arquivos 20 --linhas 50000 --repeticoes 3"
                + " --resultado dados/saida/desempenho.csv");
            return;
        }
        try {
            comparar(Opcoes.interpretar(args));
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            System.err.println("Comparação interrompida.");
            System.exit(1);
        } catch (Exception erro) {
            System.err.println("Erro na comparação: " + erro.getMessage());
            System.exit(1);
        }
    }

    private static void comparar(Opcoes opcoes) throws Exception {
        Path temporario = Files.createTempDirectory("vendas-desempenho-");
        try {
            Path entrada = Files.createDirectory(temporario.resolve("entrada"));
            gerarDados(entrada, opcoes.arquivos(), opcoes.linhas());
            List<String> medicoes = new ArrayList<>();
            medicoes.add("threads,repeticao,duracao_segundos");
            byte[] referencia = null;
            for (int threads : List.of(1, 2, 4, 8)) {
                List<Double> tempos = new ArrayList<>();
                Path saida = temporario.resolve("saida-" + threads);
                for (int repeticao = 0; repeticao <= opcoes.repeticoes(); repeticao++) {
                    double duracao = executar(entrada, saida, threads);
                    byte[] assinatura = assinatura(saida);
                    if (referencia == null) referencia = assinatura;
                    else if (!Arrays.equals(referencia, assinatura)) {
                        throw new IllegalStateException("Os relatórios diferem entre as execuções.");
                    }
                    // Descarta a primeira execução de cada configuração.
                    if (repeticao > 0) {
                        tempos.add(duracao);
                        medicoes.add(String.format(Locale.ROOT, "%d,%d,%.6f",
                            threads, repeticao, duracao));
                    }
                }
                tempos.sort(Double::compareTo);
                int meio = tempos.size() / 2;
                double mediana = tempos.size() % 2 == 0
                    ? (tempos.get(meio - 1) + tempos.get(meio)) / 2
                    : tempos.get(meio);
                System.out.printf(Locale.ROOT, "%d threads: mediana %.3f s%n", threads, mediana);
            }
            Path resultado = opcoes.resultado().toAbsolutePath();
            Files.createDirectories(resultado.getParent());
            Files.write(resultado, medicoes);
            System.out.println("Medições: " + resultado);
            System.out.println("Os tempos incluem a inicialização de outra JVM em cada execução"
                + " e sofrem influência do cache do sistema.");
        } finally {
            limpar(temporario);
        }
    }

    private static void gerarDados(Path entrada, int arquivos, int linhas) throws IOException {
        for (int loja = 0; loja < arquivos; loja++) {
            Path arquivo = entrada.resolve(String.format(Locale.ROOT, "loja-%03d.csv", loja));
            try (BufferedWriter escritor = Files.newBufferedWriter(arquivo)) {
                escritor.write("id_venda,data,produto,quantidade,preco_unitario\n");
                for (int linha = 0; linha < linhas; linha++) {
                    escritor.write("V" + linha + ",2026-10-01,Produto-" + (linha % 10) + ",2,3.50\n");
                }
            }
        }
    }

    private static double executar(Path entrada, Path saida, int threads) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classes = Path.of(Main.class.getProtectionDomain().getCodeSource()
            .getLocation().toURI()).toString();
        long inicio = System.nanoTime();
        Process processo = new ProcessBuilder(java, "-cp", classes, Main.class.getName(),
            "--entrada", entrada.toString(), "--saida", saida.toString(),
            "--threads", Integer.toString(threads))
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            if (!processo.waitFor(5, TimeUnit.MINUTES)) {
                throw new IOException("Uma execução ultrapassou cinco minutos.");
            }
            if (processo.exitValue() != 0) {
                throw new IOException("A aplicação encerrou com código " + processo.exitValue());
            }
            return (System.nanoTime() - inicio) / 1_000_000_000.0;
        } finally {
            if (processo.isAlive()) {
                processo.destroyForcibly();
                processo.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static byte[] assinatura(Path saida) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String nome : List.of("resumo-por-loja.csv", "resumo-por-produto.csv", "erros.csv")) {
            digest.update(Files.readAllBytes(saida.resolve(nome)));
        }
        return digest.digest();
    }

    private static void limpar(Path pasta) throws IOException {
        try (Stream<Path> caminhos = Files.walk(pasta)) {
            for (Path caminho : caminhos.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(caminho);
            }
        }
    }

    private record Opcoes(int arquivos, int linhas, int repeticoes, Path resultado) {
        static Opcoes interpretar(String[] args) {
            int arquivos = 20;
            int linhas = 50000;
            int repeticoes = 3;
            Path resultado = Path.of("dados/saida/desempenho.csv");
            if (args.length % 2 != 0) {
                throw new IllegalArgumentException("Cada opção precisa de um valor.");
            }
            for (int i = 0; i < args.length; i += 2) {
                switch (args[i]) {
                    case "--arquivos" -> arquivos = Integer.parseInt(args[i + 1]);
                    case "--linhas" -> linhas = Integer.parseInt(args[i + 1]);
                    case "--repeticoes" -> repeticoes = Integer.parseInt(args[i + 1]);
                    case "--resultado" -> resultado = Path.of(args[i + 1]);
                    default -> throw new IllegalArgumentException("Opção desconhecida: " + args[i]);
                }
            }
            if (arquivos <= 0 || linhas <= 0 || repeticoes <= 0) {
                throw new IllegalArgumentException("Arquivos, linhas e repetições devem ser maiores que zero.");
            }
            return new Opcoes(arquivos, linhas, repeticoes, resultado);
        }
    }
}
