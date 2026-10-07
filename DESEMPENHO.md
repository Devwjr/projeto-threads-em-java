# Comparação de desempenho

Execução de exemplo com 20 arquivos de 50 mil vendas cada, totalizando um milhão de vendas. A ferramenta de medição fez três medições por configuração, após descartar uma execução inicial, e confirmou que os três relatórios eram idênticos entre as execuções.

Esses números são do ensaio anterior à migração da ferramenta para Java. A nova implementação mantém a geração dos dados, a comparação das mesmas configurações e a verificação dos relatórios; seus tempos precisam ser medidos novamente no ambiente desejado.

| Threads | Mediana da duração |
| --- | --- |
| 1 | 4,925 s |
| 2 | 3,847 s |
| 4 | 4,896 s |
| 8 | 6,999 s |

Neste ensaio, duas threads tiveram o menor tempo. Aumentar o número de trabalhadores não produziu uma aceleração proporcional. Isso é compatível com competição por recursos e custo de coordenação, mas as medições de duração não identificam sozinhas qual foi o gargalo.

Os tempos incluem a inicialização de uma nova JVM por execução e a escrita dos relatórios. Cache, aquecimento de cada JVM e outras tarefas no ambiente influenciam os resultados. Houve verificações de integração executadas no mesmo ambiente durante parte do ensaio, portanto esses números servem como exemplo de uso da ferramenta, não como benchmark controlado.

Para repetir no seu computador com a ferramenta Java:

```bash
mvn test
java -cp target/classes br.com.wesley.vendas.Desempenho
```

As medições individuais ficam em `dados/saida/desempenho.csv`. A massa de entrada é criada em uma pasta temporária e removida ao terminar.
