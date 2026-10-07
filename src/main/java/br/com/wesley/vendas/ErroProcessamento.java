package br.com.wesley.vendas;

// Linha nula identifica falha do arquivo inteiro.
public record ErroProcessamento(String arquivo, Long linha, String motivo) {}
