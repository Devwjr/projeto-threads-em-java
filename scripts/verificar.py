"""Verificação de integração, sem dependências externas: python3 scripts/verificar.py."""
import csv
from pathlib import Path
import subprocess
import tempfile
import unittest

RAIZ = Path(__file__).resolve().parents[1]
CABECALHO = 'id_venda,data,produto,quantidade,preco_unitario\n'


class Integracao(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compilacao = tempfile.TemporaryDirectory(prefix='vendas-classes-')
        cls.classes = Path(cls.compilacao.name)
        fontes = RAIZ / 'src/main/java/br/com/wesley/vendas'
        subprocess.run(['javac', '--release', '17', '-d', str(cls.classes),
                        *map(str, fontes.glob('*.java'))], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.compilacao.cleanup()

    def setUp(self):
        self.temporario = tempfile.TemporaryDirectory(prefix='vendas-teste-')
        self.addCleanup(self.temporario.cleanup)
        self.raiz = Path(self.temporario.name)
        self.entrada = self.raiz / 'entrada'
        self.saida = self.raiz / 'saida'
        self.entrada.mkdir()

    def executar(self, threads=4, saida=None, extras=()):
        return subprocess.run(['java', '-cp', str(self.classes),
                               'br.com.wesley.vendas.Main',
                               '--entrada', str(self.entrada),
                               '--saida', str(saida or self.saida),
                               '--threads', str(threads), *extras],
                              capture_output=True, text=True, timeout=15)

    def ler(self, nome):
        with (self.saida / nome).open(newline='', encoding='utf-8') as arquivo:
            return list(csv.DictReader(arquivo))

    def test_totais_e_determinismo(self):
        for origem in (RAIZ / 'dados/entrada').glob('*.csv'):
            (self.entrada / origem.name).write_bytes(origem.read_bytes())
        referencia = None
        for threads in [1, 2, 4]:
            resultado = self.executar(threads)
            self.assertEqual(resultado.returncode, 0, resultado.stderr)
            self.assertIn('Faturamento total: 507.00', resultado.stdout)
            self.assertIn('Unidades vendidas: 23', resultado.stdout)
            produtos = {linha['produto']: linha for linha in self.ler('resumo-por-produto.csv')}
            self.assertEqual(produtos['Caderno']['faturamento'], '94.50')
            self.assertEqual(produtos['Caneta']['unidades'], '15')
            self.assertEqual(produtos['Mochila']['faturamento'], '360.00')
            relatorios = [(self.saida / nome).read_bytes() for nome in
                          ['resumo-por-loja.csv', 'resumo-por-produto.csv', 'erros.csv']]
            if referencia is not None:
                self.assertEqual(relatorios, referencia)
            referencia = relatorios

    def test_linhas_invalidas_e_falha_de_arquivo(self):
        (self.entrada / 'loja.csv').write_text(CABECALHO +
            'A,2026-10-01,Caneta,2,3.50\n'
            'B,2026-10-01,Caneta,-1,3.50\n'
            'C,2026-02-30,Caneta,1,3.50\n'
            'D,2026-10-01,Caneta,1,\n'
            'E,2026-10-01,Caneta,1,3.501\n')
        (self.entrada / 'ruim.csv').write_text('cabecalho errado\n')
        (self.entrada / 'ignorar.txt').write_text('qualquer texto')
        resultado = self.executar()
        self.assertEqual(resultado.returncode, 0, resultado.stderr)
        self.assertIn('Faturamento total: 7.00', resultado.stdout)
        self.assertIn('Arquivos com falha: 1', resultado.stdout)
        self.assertIn('Linhas rejeitadas: 4', resultado.stdout)
        erros = self.ler('erros.csv')
        self.assertEqual(len(erros), 5)
        self.assertEqual([erro['linha'] for erro in erros[:4]], ['3', '4', '5', '6'])
        self.assertEqual(erros[-1]['linha'], '')
        self.assertEqual(len(self.ler('resumo-por-loja.csv')), 1)

    def test_falha_leitura_descarta_totais_parciais(self):
        (self.entrada / 'corrompido.csv').write_bytes(
            (CABECALHO + 'A,2026-10-01,Caneta,2,3.50\n').encode() + b'\xff\n')
        resultado = self.executar()
        self.assertEqual(resultado.returncode, 0, resultado.stderr)
        self.assertIn('Arquivos com falha: 1', resultado.stdout)
        self.assertEqual(self.ler('resumo-por-loja.csv'), [])
        self.assertEqual(self.ler('resumo-por-produto.csv'), [])

    def test_pasta_vazia(self):
        resultado = self.executar()
        self.assertEqual(resultado.returncode, 0, resultado.stderr)
        self.assertIn('Faturamento total: 0.00', resultado.stdout)
        for nome in ['resumo-por-loja.csv', 'resumo-por-produto.csv', 'erros.csv']:
            self.assertEqual(self.ler(nome), [])

    def test_argumentos_invalidos(self):
        self.assertNotEqual(self.executar(0).returncode, 0)
        self.assertNotEqual(self.executar(extras=['--desconhecido', 'x']).returncode, 0)
        self.assertNotEqual(self.executar(extras=['--threads']).returncode, 0)
        self.assertNotEqual(self.executar(saida=self.entrada).returncode, 0)
        self.entrada.rmdir()
        self.assertNotEqual(self.executar().returncode, 0)

    def test_falha_escrita(self):
        self.saida.write_text('este caminho é um arquivo')
        resultado = self.executar()
        self.assertNotEqual(resultado.returncode, 0)
        self.assertNotIn('Relatórios:', resultado.stdout)

    def test_interrupcao_trabalhador(self):
        fonte = self.raiz / 'TesteInterrupcao.java'
        fonte.write_text('''
import java.nio.file.Path;
import br.com.wesley.vendas.ProcessadorArquivo;
public class TesteInterrupcao {
    public static void main(String[] args) throws Exception {
        Thread.currentThread().interrupt();
        try {
            new ProcessadorArquivo().processar(Path.of("nao-existe.csv"));
            throw new AssertionError("Interrupção ignorada");
        } catch (InterruptedException esperado) {
            if (!Thread.currentThread().isInterrupted()) {
                throw new AssertionError("Sinal de interrupção perdido");
            }
        }
    }
}
''')
        subprocess.run(['javac', '--release', '17', '-cp', str(self.classes),
                        '-d', str(self.raiz), str(fonte)], check=True)
        subprocess.run(['java', '-cp', str(self.classes) + ':' + str(self.raiz),
                        'TesteInterrupcao'], check=True, timeout=10)


if __name__ == '__main__':
    unittest.main()
