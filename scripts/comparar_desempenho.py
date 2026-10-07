"""Gera dados temporários e compara 1, 2, 4 e 8 threads, sem dependências."""
import argparse
import csv
import hashlib
from pathlib import Path
import statistics
import subprocess
import tempfile
import time

raiz = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--arquivos', type=int, default=20)
parser.add_argument('--linhas', type=int, default=50000)
parser.add_argument('--repeticoes', type=int, default=3)
parser.add_argument('--resultado', type=Path, default=raiz / 'dados/saida/desempenho.csv')
args = parser.parse_args()
if min(args.arquivos, args.linhas, args.repeticoes) <= 0:
    parser.error('Arquivos, linhas e repetições precisam ser maiores que zero.')

with tempfile.TemporaryDirectory(prefix='vendas-desempenho-') as pasta:
    temporario = Path(pasta)
    entrada = temporario / 'entrada'
    entrada.mkdir()
    classes = temporario / 'classes'
    fontes = raiz / 'src/main/java/br/com/wesley/vendas'
    subprocess.run(['javac', '--release', '17', '-d', str(classes),
                    *map(str, fontes.glob('*.java'))], check=True)
    for loja in range(args.arquivos):
        with (entrada / f'loja-{loja:03}.csv').open('w', encoding='utf-8') as arquivo:
            arquivo.write('id_venda,data,produto,quantidade,preco_unitario\n')
            for linha in range(args.linhas):
                arquivo.write(f'V{linha},2026-10-01,Produto-{linha % 10},2,3.50\n')
    registros = []
    referencia = None
    for threads in [1, 2, 4, 8]:
        tempos = []
        for repeticao in range(args.repeticoes + 1):
            saida = temporario / f'saida-{threads}'
            inicio = time.perf_counter()
            subprocess.run(['java', '-cp', str(classes), 'br.com.wesley.vendas.Main',
                            '--entrada', str(entrada), '--saida', str(saida),
                            '--threads', str(threads)], check=True,
                           stdout=subprocess.DEVNULL)
            duracao = time.perf_counter() - inicio
            digest = hashlib.sha256(b''.join((saida / nome).read_bytes() for nome in
                ['resumo-por-loja.csv', 'resumo-por-produto.csv', 'erros.csv'])).hexdigest()
            if referencia is None:
                referencia = digest
            elif digest != referencia:
                raise RuntimeError('Os relatórios diferem entre as execuções.')
            # A primeira execução de cada configuração é descartada.
            if repeticao > 0:
                tempos.append(duracao)
                registros.append([threads, repeticao, f'{duracao:.6f}'])
        print(f'{threads} threads: mediana {statistics.median(tempos):.3f} s')
    args.resultado.parent.mkdir(parents=True, exist_ok=True)
    with args.resultado.open('w', newline='', encoding='utf-8') as arquivo:
        escritor = csv.writer(arquivo)
        escritor.writerow(['threads', 'repeticao', 'duracao_segundos'])
        escritor.writerows(registros)
    print(f'Medições: {args.resultado}')
    print('Cada execução inicia outra JVM; os tempos incluem sua inicialização e sofrem influência do cache do sistema.')
