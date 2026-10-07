package br.com.wesley.vendas;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Venda(String id, LocalDate data, String produto, int quantidade,
                    BigDecimal precoUnitario) {
    public BigDecimal valor() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }

    public static Venda interpretar(String linha) {
        String[] campos = linha.split(",", -1);
        if (campos.length != 5) {
            throw new IllegalArgumentException("Esperados 5 campos.");
        }
        for (int i = 0; i < campos.length; i++) {
            campos[i] = campos[i].trim();
            if (campos[i].isEmpty()) {
                throw new IllegalArgumentException("Campo " + (i + 1) + " está vazio.");
            }
        }
        if (!campos[1].matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("Data deve usar AAAA-MM-DD.");
        }
        LocalDate data = LocalDate.parse(campos[1]);
        int quantidade = Integer.parseInt(campos[3]);
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }
        if (!campos[4].matches("\\d+(\\.\\d{1,2})?")) {
            throw new IllegalArgumentException("Preço deve usar ponto e até duas casas decimais.");
        }
        BigDecimal preco = new BigDecimal(campos[4]);
        if (preco.signum() <= 0) {
            throw new IllegalArgumentException("Preço deve ser maior que zero.");
        }
        return new Venda(campos[0], data, campos[2], quantidade, preco);
    }
}
