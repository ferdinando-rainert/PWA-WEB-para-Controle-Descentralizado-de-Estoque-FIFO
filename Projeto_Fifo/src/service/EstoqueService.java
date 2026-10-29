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
		registrarEntrada(produtoId, localId, codigoLote, validade, qtd, usuarioId, null);
	}

	public void registrarEntrada(int produtoId, int localId, String codigoLote, LocalDate validade, BigDecimal qtd,
			int usuarioId, String uuidCliente) throws SQLException {

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
			validarUsuarioAtivo(usuarioId, c);

			int loteId = loteDAO.cadastrarLote(new Lote(produtoId, codigoLote, validade, qtd), c);
			saldoDAO.somar(loteId, localId, qtd, c);
			Movimentacao movimentacao = nova(TipoMovimentacao.ENTRADA, loteId, null, localId, qtd, usuarioId);
			movimentacao.setUuidCliente(uuidCliente);
			movimentacaoDAO.registrar(movimentacao, c);

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
		return registrarSaida(produtoId, localId, qtd, usuarioId, null);
	}

	public List<String> registrarSaida(int produtoId, int localId, BigDecimal qtd, int usuarioId, String uuidCliente)
			throws SQLException {

		validarQuantidade(qtd);

		Connection c = Conexao.getConexao();
		List<String> resumo = new ArrayList<>();
		try {
			c.setAutoCommit(false);

			LocalEstoqueDAO localDAO = new LocalEstoqueDAO(c);
			ProdutoDAO produtoDAO = new ProdutoDAO(c);
			LoteDAO loteDAO = new LoteDAO(c);
			SaldoLoteLocalDAO saldoDAO = new SaldoLoteLocalDAO(c);
			MovimentacaoDAO movimentacaoDAO = new MovimentacaoDAO(c);

			validarLocal(localDAO, localId, c);
			validarProduto(produtoDAO, produtoId, c);
			validarUsuarioAtivo(usuarioId, c);

			BigDecimal restante = qtd;
			boolean primeiraMovimentacao = true;
			for (SaldoLoteLocal s : saldoDAO.listarFifo(produtoId, localId, c)) {
				if (restante.signum() == 0) {
					break;
				}
				BigDecimal usar = restante.min(s.getQuantidade());

				saldoDAO.somar(s.getLoteId(), localId, usar.negate(), c);
				Movimentacao movimentacao = nova(TipoMovimentacao.SAIDA, s.getLoteId(), localId, null, usar, usuarioId);
				if (primeiraMovimentacao) {
					movimentacao.setUuidCliente(uuidCliente);
					primeiraMovimentacao = false;
				}
				movimentacaoDAO.registrar(movimentacao, c);

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
		return transferir(produtoId, origemId, destinoId, qtd, usuarioId, null);
	}

	public List<String> transferir(int produtoId, int origemId, int destinoId, BigDecimal qtd, int usuarioId,
			String uuidCliente) throws SQLException {

		validarQuantidade(qtd);
		if (origemId == destinoId) {
			throw new IllegalArgumentException("Origem e destino não podem ser o mesmo local.");
		}

		Connection c = Conexao.getConexao();
		List<String> resumo = new ArrayList<>();
		try {
			c.setAutoCommit(false);

			LocalEstoqueDAO localDAO = new LocalEstoqueDAO(c);
			ProdutoDAO produtoDAO = new ProdutoDAO(c);
			LoteDAO loteDAO = new LoteDAO(c);
			SaldoLoteLocalDAO saldoDAO = new SaldoLoteLocalDAO(c);
			MovimentacaoDAO movimentacaoDAO = new MovimentacaoDAO(c);

			validarLocal(localDAO, origemId, c);
			validarLocal(localDAO, destinoId, c);
			validarProduto(produtoDAO, produtoId, c);
			validarUsuarioAtivo(usuarioId, c);

			BigDecimal restante = qtd;
			boolean primeiraMovimentacao = true;
			for (SaldoLoteLocal s : saldoDAO.listarFifo(produtoId, origemId, c)) {
				if (restante.signum() == 0) {
					break;
				}
				BigDecimal usar = restante.min(s.getQuantidade());

				saldoDAO.somar(s.getLoteId(), origemId, usar.negate(), c);
				saldoDAO.somar(s.getLoteId(), destinoId, usar, c);
				Movimentacao movimentacao = nova(TipoMovimentacao.TRANSFERENCIA, s.getLoteId(), origemId, destinoId,
						usar, usuarioId);
				if (primeiraMovimentacao) {
					movimentacao.setUuidCliente(uuidCliente);
					primeiraMovimentacao = false;
				}
				movimentacaoDAO.registrar(movimentacao, c);

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
		entities.LocalEstoque local = localDAO.buscarPorId(localId, c);
		if (local == null || !local.isAtivo()) {
			throw new IllegalArgumentException("Local de estoque " + localId + " não encontrado.");
		}
	}

	private void validarProduto(ProdutoDAO produtoDAO, int produtoId, Connection c) throws SQLException {
		if (produtoDAO.buscarPorId(produtoId, c) == null) {
			throw new IllegalArgumentException("Produto " + produtoId + " não encontrado.");
		}
	}

	private void validarUsuarioAtivo(int usuarioId, Connection c) throws SQLException {
		try (java.sql.PreparedStatement stmt = c.prepareStatement(
				"SELECT id FROM usuario WHERE id = ? AND ativo = TRUE")) {
			stmt.setInt(1, usuarioId);
			try (java.sql.ResultSet rs = stmt.executeQuery()) {
				if (!rs.next()) {
					throw new IllegalArgumentException("Usuário inexistente ou inativo.");
				}
			}
		}
	}
}