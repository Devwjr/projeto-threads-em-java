package br.com.wesley.vendas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class IntegracaoTest {
    private static final String CABECALHO = "id_venda,data,produto,quantidade,preco_unitario\n";
    private static final List<String> RELATORIOS = List.of(
        "resumo-por-loja.csv", "resumo-por-produto.csv", "erros.csv");

    @TempDir
    Path pasta;
    Path entrada;
    Path saida;

    @BeforeEach
    void preparar() throws IOException {
        entrada = Files.createDirectory(pasta.resolve("entrada"));
        saida = pasta.resolve("saida");
    }

    @Test
    void totaisSaoIguaisComUmaDuasEQuatroThreads() throws Exception {
        Files.writeString(entrada.resolve("loja-centro.csv"), CABECALHO
            + "V001,2026-10-01,Caderno,2,18.90\n"
            + "V002,2026-10-01,Caneta,5,3.50\n"
            + "V003,2026-10-02,Mochila,1,120.00\n");
        Files.writeString(entrada.resolve("loja-norte.csv"), CABECALHO
            + "N001,2026-10-01,Caderno,3,18.90\n"
            + "N002,2026-10-01,Caneta,10,3.50\n"
            + "N003,2026-10-02,Mochila,2,120.00\n");
        List<String> referencia = null;
        for (int threads : List.of(1, 2, 4)) {
            Execucao execucao = executar(threads, saida);
            assertEquals(0, execucao.codigo(), execucao.texto());
            assertTrue(execucao.texto().contains("Faturamento total: 507.00"));
            assertTrue(execucao.texto().contains("Unidades vendidas: 23"));
            assertEquals(List.of("produto,unidades,faturamento", "Caderno,5,94.50",
                "Caneta,15,52.50", "Mochila,3,360.00"),
                Files.readAllLines(saida.resolve("resumo-por-produto.csv")));
            List<String> relatorios = new ArrayList<>();
            for (String nome : RELATORIOS) relatorios.add(Files.readString(saida.resolve(nome)));
            if (referencia != null) assertEquals(referencia, relatorios);
            referencia = relatorios;
        }
    }

    @Test
    void registraLinhasInvalidasSemPerderVendasValidas() throws Exception {
        Files.writeString(entrada.resolve("loja.csv"), CABECALHO
            + "A,2026-10-01,Caneta,2,3.50\n"
            + "B,2026-10-01,Caneta,-1,3.50\n"
            + "C,2026-02-30,Caneta,1,3.50\n"
            + "D,2026-10-01,Caneta,1,\n"
            + "E,2026-10-01,Caneta,1,3.501\n");
        Files.writeString(entrada.resolve("ruim.csv"), "cabecalho errado\n");
        Files.writeString(entrada.resolve("ignorar.txt"), "qualquer texto");
        Execucao execucao = executar(4, saida);
        assertEquals(0, execucao.codigo(), execucao.texto());
        assertTrue(execucao.texto().contains("Faturamento total: 7.00"));
        assertTrue(execucao.texto().contains("Arquivos concluídos: 1"));
        assertTrue(execucao.texto().contains("Arquivos com falha: 1"));
        assertTrue(execucao.texto().contains("Linhas rejeitadas: 4"));
        List<String> erros = Files.readAllLines(saida.resolve("erros.csv"));
        assertEquals(6, erros.size());
        for (int i = 1; i <= 4; i++) assertTrue(erros.get(i).startsWith("loja.csv," + (i + 2) + ","));
        assertTrue(erros.get(5).startsWith("ruim.csv,,"));
        assertEquals(2, Files.readAllLines(saida.resolve("resumo-por-loja.csv")).size());
    }

    @Test
    void falhaDeLeituraDescartaTotaisParciais() throws Exception {
        byte[] valido = (CABECALHO + "A,2026-10-01,Caneta,2,3.50\n")
            .getBytes(StandardCharsets.UTF_8);
        byte[] corrompido = java.util.Arrays.copyOf(valido, valido.length + 2);
        corrompido[valido.length] = (byte) 0xff;
        corrompido[valido.length + 1] = '\n';
        Files.write(entrada.resolve("corrompido.csv"), corrompido);
        Execucao execucao = executar(4, saida);
        assertEquals(0, execucao.codigo(), execucao.texto());
        assertTrue(execucao.texto().contains("Arquivos com falha: 1"));
        assertEquals(1, Files.readAllLines(saida.resolve("resumo-por-loja.csv")).size());
        assertEquals(1, Files.readAllLines(saida.resolve("resumo-por-produto.csv")).size());
    }

    @Test
    void pastaVaziaGeraSomenteCabecalhos() throws Exception {
        Execucao execucao = executar(4, saida);
        assertEquals(0, execucao.codigo(), execucao.texto());
        assertTrue(execucao.texto().contains("Faturamento total: 0.00"));
        for (String nome : RELATORIOS) assertEquals(1, Files.readAllLines(saida.resolve(nome)).size());
    }

    @Test
    void rejeitaArgumentosInvalidos() throws Exception {
        assertNotEquals(0, executar(0, saida).codigo());
        assertNotEquals(0, executar(4, saida, "--desconhecido", "x").codigo());
        assertNotEquals(0, executar(4, saida, "--threads").codigo());
        assertNotEquals(0, executar(4, entrada).codigo());
        Files.delete(entrada);
        assertNotEquals(0, executar(4, saida).codigo());
    }

    @Test
    void falhaDeEscritaEncerraComErro() throws Exception {
        Files.writeString(saida, "este caminho é um arquivo");
        Execucao execucao = executar(4, saida);
        assertNotEquals(0, execucao.codigo());
        assertFalse(execucao.texto().contains("Relatórios:"));
    }

    @Test
    void trabalhadorRespeitaInterrupcao() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class,
                () -> new ProcessadorArquivo().processar(pasta.resolve("nao-existe.csv")));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            // Limpa apenas o sinal usado pelo teste para não afetar o JUnit.
            Thread.interrupted();
        }
    }

    private Execucao executar(int threads, Path destino, String... extras) throws Exception {
        // Outra JVM permite verificar System.exit e o encerramento real do executor.
        String classes = Path.of(Main.class.getProtectionDomain().getCodeSource()
            .getLocation().toURI()).toString();
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> comando = new ArrayList<>(List.of(java, "-cp", classes,
            Main.class.getName(), "--entrada", entrada.toString(), "--saida", destino.toString(),
            "--threads", Integer.toString(threads)));
        comando.addAll(List.of(extras));
        File log = Files.createTempFile(pasta, "execucao-", ".log").toFile();
        Process processo = new ProcessBuilder(comando).redirectErrorStream(true)
            .redirectOutput(log).start();
        try {
            assertTrue(processo.waitFor(15, TimeUnit.SECONDS), "Aplicação não encerrou em 15 segundos.");
            return new Execucao(processo.exitValue(), Files.readString(log.toPath()));
        } finally {
            if (processo.isAlive()) {
                processo.destroyForcibly();
                processo.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private record Execucao(int codigo, String texto) {}
}
