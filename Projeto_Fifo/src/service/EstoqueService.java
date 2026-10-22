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

public class EstoqueService {

	public void registrarEntrada(int produtoId, int localId, String codigoLote, LocalDate validade, BigDecimal qtd,
			int usuarioId) throws SQLException {

		validarQuantidade(qtd);

		Connection c = Conexao.getConexao();
		try {
			c.setAutoCommit(false);

			ProdutoDAO produtoDAO = new ProdutoDAO(c);
			LocalEstoqueDAO localDAO = new LocalEstoqueDAO(c);
			LoteDAO loteDAO = new LoteDAO(c);
			SaldoLoteLocalDAO saldoDAO = new SaldoLoteLocalDAO(c);
			MovimentacaoDAO movimentacaoDAO = new MovimentacaoDAO(c);

			Produto produto = produtoDAO.buscarPorId(produtoId, c);
			if (produto == null || !produto.isAtivo()) {
				throw new IllegalArgumentException("Produto inexistente ou inativo (não aceita novas entradas).");
			}
			validarLocal(localDAO, localId, c);

			int loteId = loteDAO.cadastrarLote(new Lote(produtoId, codigoLote, validade, qtd), c);
			saldoDAO.somar(loteId, localId, qtd, c);
			movimentacaoDAO.registrar(nova(TipoMovimentacao.ENTRADA, loteId, null, localId, qtd, usuarioId), c);

			c.commit();
		} catch (SQLException | RuntimeException e) {
			c.rollback();
			throw e;
		} finally {
			if (c != null && !c.isClosed()) {
				c.setAutoCommit(true);
				c.close();
			}
		}
	}

	public List<String> registrarSaida(int produtoId, int localId, BigDecimal qtd, int usuarioId)
			throws SQLException {

		validarQuantidade(qtd);

		Connection c = Conexao.getConexao();
		List<String> resumo = new ArrayList<>();
		try {
			c.setAutoCommit(false);

			LocalEstoqueDAO localDAO = new LocalEstoqueDAO(c);
			LoteDAO loteDAO = new LoteDAO(c);
			SaldoLoteLocalDAO saldoDAO = new SaldoLoteLocalDAO(c);
			MovimentacaoDAO movimentacaoDAO = new MovimentacaoDAO(c);

			validarLocal(localDAO, localId, c);

			BigDecimal restante = qtd;
			for (SaldoLoteLocal s : saldoDAO.listarFifo(produtoId, localId, c)) {
				if (restante.signum() == 0) {
					break;
				}
				BigDecimal usar = restante.min(s.getQuantidade());

				saldoDAO.somar(s.getLoteId(), localId, usar.negate(), c);
				movimentacaoDAO.registrar(nova(TipoMovimentacao.SAIDA, s.getLoteId(), localId, null, usar, usuarioId), c);

				resumo.add(descreverLote(loteDAO, s.getLoteId(), c) + " -> " + usar);
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
			if (c != null && !c.isClosed()) {
				c.setAutoCommit(true);
				c.close();
			}
		}
		return resumo;
	}

	public List<String> transferir(int produtoId, int origemId, int destinoId, BigDecimal qtd, int usuarioId)
			throws SQLException {

		validarQuantidade(qtd);
		if (origemId == destinoId) {
			throw new IllegalArgumentException("Origem e destino não podem ser o mesmo local.");
		}

		Connection c = Conexao.getConexao();
		List<String> resumo = new ArrayList<>();
		try {
			c.setAutoCommit(false);

			LocalEstoqueDAO localDAO = new LocalEstoqueDAO(c);
			LoteDAO loteDAO = new LoteDAO(c);
			SaldoLoteLocalDAO saldoDAO = new SaldoLoteLocalDAO(c);
			MovimentacaoDAO movimentacaoDAO = new MovimentacaoDAO(c);

			validarLocal(localDAO, origemId, c);
			validarLocal(localDAO, destinoId, c);

			BigDecimal restante = qtd;
			for (SaldoLoteLocal s : saldoDAO.listarFifo(produtoId, origemId, c)) {
				if (restante.signum() == 0) {
					break;
				}
				BigDecimal usar = restante.min(s.getQuantidade());

				saldoDAO.somar(s.getLoteId(), origemId, usar.negate(), c);
				saldoDAO.somar(s.getLoteId(), destinoId, usar, c);
				movimentacaoDAO.registrar(
						nova(TipoMovimentacao.TRANSFERENCIA, s.getLoteId(), origemId, destinoId, usar, usuarioId), c);

				resumo.add(descreverLote(loteDAO, s.getLoteId(), c) + " -> " + usar);
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
			if (c != null && !c.isClosed()) {
				c.setAutoCommit(true);
				c.close();
			}
		}
		return resumo;
	}

	private Movimentacao nova(TipoMovimentacao tipo, int loteId, Integer origem, Integer destino, BigDecimal qtd,
			int usuarioId) {
		return new Movimentacao(tipo, loteId, origem, destino, qtd, usuarioId);
	}

	private String descreverLote(LoteDAO loteDAO, int loteId, Connection c) throws SQLException {
		Lote l = loteDAO.buscarPorId(loteId, c);
		return l != null ? "Lote " + l.getCodigoLote() + " (id " + loteId + ")" : "Lote " + loteId;
	}

	private void validarQuantidade(BigDecimal qtd) {
		if (qtd == null || qtd.signum() <= 0) {
			throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
		}
	}

	private void validarLocal(LocalEstoqueDAO localDAO, int localId, Connection c) throws SQLException {
		if (localDAO.buscarPorId(localId, c) == null) {
			throw new IllegalArgumentException("Local de estoque " + localId + " não encontrado.");
		}
	}
}