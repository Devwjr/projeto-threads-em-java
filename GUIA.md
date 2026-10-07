# Projeto: processador paralelo de relatórios de vendas

> Estado atual: as etapas principais deste guia estão implementadas. Consulte o
> `README.md` para executar a aplicação e `scripts/verificar.py` para verificar
> os cenários. Dos desafios extras, foram implementados o recebimento por ordem
> de conclusão, o limite de tarefas em andamento e a escrita temporária de
> relatórios. Timeout e suporte a CSV completo continuam como exercícios opcionais.

Você vai construir uma ferramenta de terminal que lê vários arquivos CSV de vendas em paralelo e gera um relatório consolidado. É uma aplicação real: uma empresa pode receber um arquivo por loja e precisar juntar os dados para analisar seu faturamento diário.

**Nível:** intermediário. **Tecnologias:** Java 17 ou superior, biblioteca padrão e, se quiser, Maven. Não precisa de Spring, banco de dados ou interface gráfica.

Este guia dá a direção e os critérios para verificar seu trabalho. A implementação fica com você.

## 1. O que a aplicação deve fazer

Ao executar o programa, você informa uma pasta de entrada, uma pasta de saída e o número de trabalhadores. Ele deve:

1. Encontrar os arquivos `.csv` da pasta de entrada, sem entrar em subpastas.
2. Processar vários arquivos simultaneamente, com um limite de threads.
3. Validar cada venda e registrar linhas inválidas sem interromper todo o processamento.
4. Consolidar os resultados dos arquivos que conseguiu ler.
5. Gerar os relatórios e mostrar um resumo da execução no terminal.

Uma execução poderia receber estes argumentos:

```text
--entrada dados/entrada --saida dados/saida --threads 4
```

No final, mostre quantidade de arquivos concluídos e com falha, vendas válidas, linhas rejeitadas, faturamento e duração total.

## 2. Defina um formato simples de entrada

Cada arquivo representa uma loja. O nome do arquivo identifica a loja, por exemplo `loja-centro.csv`.

```csv
id_venda,data,produto,quantidade,preco_unitario
V001,2026-10-01,Caderno,2,18.90
V002,2026-10-01,Caneta,5,3.50
V003,2026-10-02,Mochila,1,120.00
```

Para o primeiro escopo, assuma UTF-8, cabeçalho obrigatório, campos sem vírgulas internas e sem aspas. Essa restrição permite começar sem implementar um parser CSV completo. Documente-a na aplicação.

Regras:

- Data no formato `AAAA-MM-DD`.
- ID e produto não podem estar vazios.
- Quantidade deve ser um inteiro maior que zero.
- Preço deve ser maior que zero, com no máximo duas casas decimais e ponto como separador.
- Valor da venda = quantidade × preço unitário.
- IDs são únicos dentro de cada arquivo; por enquanto, não implemente deduplicação.

Use `BigDecimal` para dinheiro e `LocalDate` para datas. Construa o preço a partir do texto, sem passar por `double`.

## 3. Entregas esperadas

Gere três arquivos na pasta de saída:

| Arquivo | Conteúdo |
| --- | --- |
| `resumo-por-loja.csv` | Loja, quantidade de vendas válidas, unidades vendidas e faturamento |
| `resumo-por-produto.csv` | Produto, unidades vendidas e faturamento, somando todas as lojas |
| `erros.csv` | Arquivo, número da linha física e motivo da rejeição ou falha |

Ordene os relatórios por nome de loja ou produto para que o resultado seja previsível. Nos erros, inclua também arquivos ilegíveis ou com cabeçalho inválido. Falhas de arquivo podem usar número de linha vazio.

**Política de falha:** uma linha inválida é descartada; o restante do arquivo continua. Um arquivo com falha de leitura ou cabeçalho inválido não contribui para os totais, mesmo que parte dele tenha sido lida. Os demais arquivos continuam. Se não for possível escrever os relatórios, informe a falha e encerre com erro.

## 4. Organização sugerida

Você pode separar as responsabilidades assim; os nomes são sugestões:

```text
src/main/java/.../
  Main.java                  # argumentos e início da aplicação
  Venda.java                 # dados de uma venda válida
  ProcessadorArquivo.java    # leitura, validação e resumo de um arquivo
  ResultadoArquivo.java      # totais locais, rejeições e identificação da loja
  Consolidador.java           # soma resultados já concluídos
  GeradorRelatorio.java       # escrita dos CSVs finais
```

Comece com poucos arquivos e extraia classes conforme as responsabilidades ficarem claras. Não precisa criar uma arquitetura grande.

## 5. Etapa 1 — Faça funcionar com um arquivo

Implemente primeiro o fluxo sequencial:

1. Abra um arquivo usando `Files.newBufferedReader` e `try-with-resources`.
2. Confira o cabeçalho.
3. Leia uma linha por vez, preservando o número da linha física.
4. Separe os campos, preservando campos vazios ao final da linha.
5. Valide e converta os valores.
6. Acumule os totais da loja e de cada produto.
7. Registre os erros de validação com uma mensagem compreensível.

Não carregue todas as vendas na memória: mantenha apenas os totais e os registros de erro. Nesta primeira versão, os erros podem ficar em memória; muitos erros poderão exigir outra estratégia no futuro.

**Concluiu quando:** o CSV de exemplo produz 3 vendas válidas, 8 unidades e faturamento de **175.30**. Uma linha inválida adicional deve aparecer nos erros sem mudar esses totais.

## 6. Etapa 2 — Processe uma pasta sequencialmente

Descubra os arquivos da pasta e reutilize o processador para cada um. Feche também o recurso usado para listar os arquivos.

Implemente a consolidação e gere os três relatórios. Valide argumentos, existência da pasta de entrada e número de threads maior que zero. Uma pasta vazia deve terminar normalmente, com totais zero e relatórios contendo apenas os cabeçalhos.

**Concluiu quando:** dois arquivos com o mesmo produto geram um único total desse produto no relatório consolidado.

Guarde essa versão: ela será sua referência para verificar a versão paralela.

## 7. Etapa 3 — Introduza threads em paralelo

Aqui está o centro do projeto. Use um `ExecutorService` criado com `Executors.newFixedThreadPool`. Cada tarefa processa **um arquivo inteiro** e retorna um `ResultadoArquivo`.

Fluxo que você deve implementar:

```text
Thread principal encontra os arquivos
    → submete uma tarefa por arquivo ao pool
    → recebe os resultados das tarefas
    → consolida os resultados
    → escreve os relatórios
```

Direções para pesquisar e aplicar:

- `Callable<ResultadoArquivo>` para uma tarefa que devolve um resultado.
- `Future<ResultadoArquivo>` para obter esse resultado e observar falhas.
- `ExecutorService` para controlar a execução e o ciclo de vida do pool.
- `ExecutionException` para identificar uma tarefa que falhou.

**Decisão importante:** cada tarefa mantém seus próprios mapas e contadores. A thread principal consolida os resultados recebidos. Assim, os trabalhadores não alteram o mesmo `HashMap`, e você evita disputas de acesso desnecessárias.

Não crie uma thread por linha nem escreva o mesmo relatório a partir de várias tarefas. O pool limita os trabalhadores ativos; submeter tarefas não significa que todas começam imediatamente.

Para começar, guarde os `Future`s e consulte cada um com `get()`. Esperar por eles na ordem de submissão não impede que as tarefas executem em paralelo, mas pode atrasar a exibição de resultados que já terminaram.

**Concluiu quando:** com 1, 2 e 4 trabalhadores, os relatórios têm os mesmos totais da versão sequencial. Com vários arquivos, logs contendo arquivo e nome da thread mostram diferentes trabalhadores atuando durante a execução. Não confunda logs intercalados com prova de ganho de desempenho.

## 8. Etapa 4 — Trate falhas e encerramento

- Capture erros de validação no processamento da linha; não use um `catch` genérico que esconda qualquer problema.
- Quando uma tarefa falhar, identifique o arquivo correspondente e registre a falha, sem perder os resultados dos demais.
- Garanta o encerramento do executor mesmo se a consolidação ou a escrita falhar.
- Estude `shutdown`, `awaitTermination` e `shutdownNow`: solicitar encerramento não é o mesmo que aguardar seu término.
- Se a thread principal for interrompida, cancele as tarefas pendentes e preserve o sinal de interrupção com `Thread.currentThread().interrupt()`.
- Para as tarefas responderem ao cancelamento, verifique a interrupção durante a leitura. `cancel(true)` e `shutdownNow()` solicitam interrupção; não forçam uma tarefa a parar.

**Concluiu quando:** um arquivo defeituoso não derruba os demais; o programa termina sem deixar trabalhadores ativos; interromper o processamento não gera relatórios apresentados como completos.

## 9. Etapa 5 — Comprove a correção e compare desempenho

Monte pequenos arquivos com resultado conhecido antes de usar uma massa grande.

| Cenário | Resultado esperado |
| --- | --- |
| CSV de exemplo | 3 vendas, 8 unidades, 175.30 de faturamento |
| Quantidade negativa | Linha rejeitada e totais válidos preservados |
| Campo final vazio | Linha rejeitada por preço ausente |
| Data impossível | Linha rejeitada por data inválida |
| Cabeçalho errado | Arquivo registrado como falha, sem contribuição nos totais |
| Mesmo produto em duas lojas | Totais do produto somados corretamente |
| Pasta vazia | Totais zero, sem travamento |
| 1, 2 e 4 trabalhadores | Mesmos dados nos relatórios |

Depois, crie uma massa maior, por exemplo 20 arquivos com 50 mil linhas cada. Você pode escrever um gerador simples como ferramenta auxiliar.

Meça com `System.nanoTime()`. Compare 1, 2, 4 e 8 trabalhadores, repetindo cada configuração pelo menos três vezes e mantendo a mesma entrada. Registre a duração e a mediana; considere o aquecimento da JVM e o cache do sistema operacional ao interpretar os números.

Não use `Thread.sleep` para fabricar ganho de velocidade. Mais threads podem piorar o tempo por competição pelo disco, CPU e memória. Um resultado sem aceleração também ensina algo: explique o gargalo observado, sem prometer que quatro threads serão quatro vezes mais rápidas.

## 10. Desafios extras, depois do básico

Escolha um por vez:

1. **Progresso por ordem de conclusão:** use `ExecutorCompletionService` para receber primeiro os arquivos que terminarem primeiro.
2. **Timeout:** defina um limite de tempo e distinga tarefas concluídas, canceladas e com falha.
3. **Muitos arquivos:** limite também as tarefas em espera; um pool fixo limita threads, mas sua fila pode crescer com milhares de submissões.
4. **Publicação dos relatórios:** escreva primeiro arquivos temporários para reduzir o risco de deixar relatórios parcialmente escritos.
5. **CSV completo:** adote uma biblioteca de parsing para aceitar campos com aspas e vírgulas internas.

## Por onde começar hoje

Crie manualmente `dados/entrada/loja-centro.csv` com o exemplo. Implemente apenas a leitura, a validação e o cálculo dos **175.30**. Depois avance pelas etapas; introduza o pool quando a versão sequencial já estiver correta.

Ao terminar, você terá praticado uma aplicação útil com tarefas independentes, resultados assíncronos, consolidação, tratamento de falhas e encerramento de threads — e terá uma base concreta para explicar por que usou concorrência.
