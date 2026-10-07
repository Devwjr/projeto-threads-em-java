# Processamento de vendas com threads em Java

Projeto de estudo para demonstrar meus conhecimentos em Java e praticar o processamento de tarefas em paralelo com threads.

A aplicação lê arquivos CSV de diferentes lojas, valida as vendas e consolida o faturamento por loja e produto. Cada arquivo é uma tarefa independente, executada por um pool de threads. A consolidação acontece na thread principal.

## Conceitos praticados

- Leitura de arquivos e fechamento de recursos com `try-with-resources`.
- Validação, tratamento de exceções e cálculos com `BigDecimal`.
- Organização em classes e uso de `record`.
- `ExecutorService`, tarefas `Callable` e resultados `Future`.
- `ExecutorCompletionService` para receber tarefas por ordem de conclusão.
- Limite de tarefas em andamento, interrupção e encerramento do executor.
- Geração de relatórios e comparação de desempenho.

## Como executar

Com JDK 17 ou superior, execute na pasta do projeto:

```bash
javac --release 17 -d target/classes src/main/java/br/com/wesley/vendas/*.java
java -cp target/classes br.com.wesley.vendas.Main --entrada dados/entrada --saida dados/saida --threads 4
```

Os argumentos são opcionais; esses são os valores padrão. Use `--help` para consultar a forma de execução. As pastas de entrada e saída precisam ser diferentes.

Com os arquivos de exemplo, o resultado é de 6 vendas válidas, 23 unidades e faturamento total de `507.00`. A ordem das mensagens de conclusão pode variar; os relatórios são ordenados.

## Entrada e relatórios

O programa encontra os arquivos `.csv` diretamente na pasta de entrada, sem percorrer subpastas. Cada arquivo representa uma loja.

```csv
id_venda,data,produto,quantidade,preco_unitario
V001,2026-10-01,Caderno,2,18.90
```

A entrada usa UTF-8, cabeçalho obrigatório, campos sem aspas e sem vírgulas internas. Quantidade e preço devem ser positivos. Preços usam ponto e até duas casas decimais. IDs são considerados únicos por arquivo; não há deduplicação.

Os relatórios são gerados na pasta de saída:

| Arquivo | Conteúdo |
| --- | --- |
| `resumo-por-loja.csv` | Vendas válidas, unidades e faturamento por loja |
| `resumo-por-produto.csv` | Unidades e faturamento por produto, somando as lojas |
| `erros.csv` | Arquivo, linha física e motivo do erro |

Linhas inválidas são descartadas. Arquivos com cabeçalho inválido ou falha de leitura não contribuem para os totais; os demais continuam. Falhas de arquivo aparecem nos erros com o campo de linha vazio.

Uma pasta vazia gera relatórios contendo apenas os cabeçalhos. Falhas de configuração, escrita ou interrupção encerram o programa com código de erro. Arquivos de entrada inválidos são registrados no relatório sem impedir a conclusão dos demais.

Cada relatório é escrito em um arquivo temporário antes de substituir seu destino. A substituição dos três relatórios não é uma transação: uma falha de escrita pode deixar arquivos de execuções diferentes na pasta. A aplicação só anuncia sucesso após gerar os três.

## Verificações e desempenho

Com Python 3 e o JDK instalados, execute as verificações de integração:

```bash
python3 scripts/verificar.py
```

Para gerar 20 arquivos com 50 mil vendas cada e comparar 1, 2, 4 e 8 threads, com três medições por configuração:

```bash
python3 scripts/comparar_desempenho.py
```

O script usa dados temporários, confirma que os relatórios são iguais e salva as medições em `dados/saida/desempenho.csv`. Para uma execução menor:

```bash
python3 scripts/comparar_desempenho.py --arquivos 4 --linhas 10000
```

As medições incluem a inicialização de uma nova JVM por execução. A primeira execução de cada configuração é descartada, mas isso não mantém a JVM aquecida entre medições. CPU, disco, memória e cache influenciam o resultado; mais threads não garantem mais velocidade.

## Escopo

As etapas principais do [GUIA.md](GUIA.md) estão implementadas. Permanecem como exercícios opcionais o timeout por tarefa e a adoção de uma biblioteca para aceitar CSV com aspas e vírgulas internas. A lista de caminhos e os registros de erro ficam em memória; a quantidade de tarefas em andamento é limitada ao número de trabalhadores.
