package service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import Conexao.Conexao;
import DAO.LocalEstoqueDAO;
import DAO.LoteDAO;
import DAO.MovimentacaoDAO;
import DAO.ProdutoDAO;
import DAO.SaldoLoteLocalDAO;
import entities.Lote;
import entities.Movimentacao;
import entities.Produto;
import entities.SaldoLoteLocal;
import entities.TipoMovimentacao;

// Regras de negócio do estoque: entrada, saída FIFO e transferência.
// Cada operação roda numa transação: ou grava tudo, ou desfaz tudo (rollback).
public class EstoqueService {

	private final ProdutoDAO produtoDAO = new ProdutoDAO();
	private final LocalEstoqueDAO localDAO = new LocalEstoqueDAO();
	private final LoteDAO loteDAO = new LoteDAO();
	private final SaldoLoteLocalDAO saldoDAO = new SaldoLoteLocalDAO();
	private final MovimentacaoDAO movimentacaoDAO = new MovimentacaoDAO();

	// ENTRADA: cria um lote novo e soma o saldo no local de destino
	public void registrarEntrada(int produtoId, int localId, String codigoLote, LocalDate validade, BigDecimal qtd,
			int usuarioId) throws SQLException {

		validarQuantidade(qtd);
		Produto produto = produtoDAO.buscarPorId(produtoId);
		if (produto == null || !produto.isAtivo()) {
			throw new IllegalArgumentException("Produto inexistente ou inativo (não aceita novas entradas).");
		}
		validarLocal(localId);

		Connection c = Conexao.getConexao();
		try {
			c.setAutoCommit(false);

			int loteId = loteDAO.cadastrarLote(new Lote(produtoId, codigoLote, validade, qtd));
			saldoDAO.somar(loteId, localId, qtd);
			movimentacaoDAO.registrar(nova(TipoMovimentacao.ENTRADA, loteId, null, localId, qtd, usuarioId));

			c.commit();
		} catch (SQLException | RuntimeException e) {
			c.rollback();
			throw e;
		} finally {
			c.setAutoCommit(true);
		}
	}

	// SAÍDA FIFO: consome primeiro o lote mais antigo; se não bastar, passa para o próximo
	public List<String> registrarSaida(int produtoId, int localId, BigDecimal qtd, int usuarioId)
			throws SQLException {

		validarQuantidade(qtd);
		validarLocal(localId);

		Connection c = Conexao.getConexao();
		List<String> resumo = new ArrayList<>();
		try {
			c.setAutoCommit(false);

			BigDecimal restante = qtd;
			for (SaldoLoteLocal s : saldoDAO.listarFifo(produtoId, localId)) {
				if (restante.signum() == 0) {
					break;
				}
				BigDecimal usar = restante.min(s.getQuantidade());

				saldoDAO.somar(s.getLoteId(), localId, usar.negate());
				movimentacaoDAO.registrar(nova(TipoMovimentacao.SAIDA, s.getLoteId(), localId, null, usar, usuarioId));

				resumo.add(descreverLote(s.getLoteId()) + " -> " + usar);
				restante = restante.subtract(usar);
			}

			if (restante.signum() > 0) {
				throw new IllegalArgumentException("Saldo insuficiente: faltam " + restante + " unidade(s).");
			}

			c.commit();
		} catch (SQLException | RuntimeException e) {
			c.rollback();
			throw e;
		} finally {
			c.setAutoCommit(true);
		}
		return resumo;
	}

	// TRANSFERÊNCIA: tira do local de origem (em ordem FIFO) e coloca no local de destino
	public List<String> transferir(int produtoId, int origemId, int destinoId, BigDecimal qtd, int usuarioId)
			throws SQLException {

		validarQuantidade(qtd);
		if (origemId == destinoId) {
			throw new IllegalArgumentException("Origem e destino não podem ser o mesmo local.");
		}
		validarLocal(origemId);
		validarLocal(destinoId);

		Connection c = Conexao.getConexao();
		List<String> resumo = new ArrayList<>();
		try {
			c.setAutoCommit(false);

			BigDecimal restante = qtd;
			for (SaldoLoteLocal s : saldoDAO.listarFifo(produtoId, origemId)) {
				if (restante.signum() == 0) {
					break;
				}
				BigDecimal usar = restante.min(s.getQuantidade());

				saldoDAO.somar(s.getLoteId(), origemId, usar.negate());
				saldoDAO.somar(s.getLoteId(), destinoId, usar);
				movimentacaoDAO.registrar(
						nova(TipoMovimentacao.TRANSFERENCIA, s.getLoteId(), origemId, destinoId, usar, usuarioId));

				resumo.add(descreverLote(s.getLoteId()) + " -> " + usar);
				restante = restante.subtract(usar);
			}

			if (restante.signum() > 0) {
				throw new IllegalArgumentException("Saldo insuficiente na origem: faltam " + restante + " unidade(s).");
			}

			c.commit();
		} catch (SQLException | RuntimeException e) {
			c.rollback();
			throw e;
		} finally {
			c.setAutoCommit(true);
		}
		return resumo;
	}

	private Movimentacao nova(TipoMovimentacao tipo, int loteId, Integer origem, Integer destino, BigDecimal qtd,
			int usuarioId) {
		return new Movimentacao(tipo, loteId, origem, destino, qtd, usuarioId);
	}

	private String descreverLote(int loteId) {
		Lote l = loteDAO.buscarPorId(loteId);
		return l != null ? "Lote " + l.getCodigoLote() + " (id " + loteId + ")" : "Lote " + loteId;
	}

	private void validarQuantidade(BigDecimal qtd) {
		if (qtd == null || qtd.signum() <= 0) {
			throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
		}
	}

	private void validarLocal(int localId) {
		if (localDAO.buscarPorId(localId) == null) {
			throw new IllegalArgumentException("Local de estoque " + localId + " não encontrado.");
		}
	}

}
