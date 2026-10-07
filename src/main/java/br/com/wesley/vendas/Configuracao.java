package br.com.wesley.vendas;

import java.nio.file.Files;
import java.nio.file.Path;

public record Configuracao(Path entrada, Path saida, int threads) {
    public static Configuracao interpretar(String[] args) {
        Path entrada = Path.of("dados/entrada");
        Path saida = Path.of("dados/saida");
        int threads = 4;
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Cada opção precisa de um valor.");
        }
        for (int i = 0; i < args.length; i += 2) {
            switch (args[i]) {
                case "--entrada" -> entrada = Path.of(args[i + 1]);
                case "--saida" -> saida = Path.of(args[i + 1]);
                case "--threads" -> threads = Integer.parseInt(args[i + 1]);
                default -> throw new IllegalArgumentException("Opção desconhecida: " + args[i]);
            }
        }
        if (threads <= 0) {
            throw new IllegalArgumentException("Número de threads deve ser maior que zero.");
        }
        if (!Files.isDirectory(entrada)) {
            throw new IllegalArgumentException("Pasta de entrada não existe: " + entrada);
        }
        Path origem = entrada.toAbsolutePath().normalize();
        Path destino = saida.toAbsolutePath().normalize();
        if (origem.equals(destino) || (Files.exists(saida) && mesmaPasta(entrada, saida))) {
            throw new IllegalArgumentException("Entrada e saída devem ser pastas diferentes.");
        }
        return new Configuracao(entrada, saida, threads);
    }

    private static boolean mesmaPasta(Path entrada, Path saida) {
        try {
            return Files.isSameFile(entrada, saida);
        } catch (java.io.IOException erro) {
            throw new IllegalArgumentException("Não foi possível verificar a pasta de saída.", erro);
        }
    }
}
