package br.com.wesley.vendas;

import java.io.IOException;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) {
        Path arquivo = Path.of("dados/entrada/loja-centro.csv");
        ProcessadorArquivo processador = new ProcessadorArquivo();

        try {
            ResultadoArquivo resultado = processador.processar(arquivo);

            System.out.println("Arquivo: " + resultado.nomeArquivo());
            System.out.println("Vendas válidas: " + resultado.vendasValidas());
            System.out.println(
                "Linhas rejeitadas: " + resultado.linhasRejeitadas()
            );
            System.out.println(
                "Faturamento total: " + resultado.faturamento().toPlainString()
            );

        } catch (IOException | IllegalArgumentException erro) {
            System.err.println(
                "Erro ao processar " + arquivo + ": " + erro.getMessage()
            );
            System.exit(1);
        }
    }
}
