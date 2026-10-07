# Processamento de vendas com threads em Java

Projeto de estudo para demonstrar meus conhecimentos em Java e praticar o processamento de tarefas em paralelo com threads.

A aplicação lê arquivos CSV de vendas de diferentes lojas, valida os registros e calcula o faturamento de cada arquivo e o total geral. Cada arquivo é processado como uma tarefa independente em um pool de até quatro threads.

## Conceitos praticados

- Leitura de arquivos com `Files` e `BufferedReader`.
- Validação de dados e tratamento de exceções.
- Cálculos monetários com `BigDecimal`.
- Organização do código em classes e uso de `record`.
- Execução paralela com `ExecutorService`, `Callable` e `Future`.
- Consolidação dos resultados na thread principal.
- Encerramento do executor e tratamento de interrupções.

## Funcionamento

Os arquivos de entrada ficam em `dados/entrada`. O programa procura os arquivos `.csv` dessa pasta e exibe no terminal o faturamento, a quantidade de vendas válidas e as linhas rejeitadas.

Linhas inválidas são descartadas. Se um arquivo falhar, os demais continuam sendo processados. Os relatórios em arquivo ainda não estão implementados.

Formato de entrada:

```csv
id_venda,data,produto,quantidade,preco_unitario
V001,2026-10-01,Caderno,2,18.90
```

Esta versão aceita campos sem aspas e sem vírgulas internas. Os preços usam ponto como separador decimal.

## Como executar

É necessário ter o JDK 17 ou superior instalado. Na pasta do projeto, execute:

```bash
javac --release 17 -d target/classes src/main/java/br/com/wesley/vendas/*.java
java -cp target/classes br.com.wesley.vendas.Main
```

Com os dois arquivos de exemplo, o resultado esperado é de 6 vendas válidas e faturamento total de `507.00`.

## Desenvolvimento

O projeto está sendo desenvolvido por etapas, começando pelo processamento sequencial e evoluindo para a execução paralela. O [GUIA.md](GUIA.md) contém o roteiro de estudo e as próximas melhorias.
