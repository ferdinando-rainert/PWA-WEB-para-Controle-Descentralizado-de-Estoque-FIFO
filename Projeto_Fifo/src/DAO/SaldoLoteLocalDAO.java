package DAO;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import Conexao.Conexao;
import entities.SaldoLoteLocal;

public class SaldoLoteLocalDAO {

	private Connection conn;

	public SaldoLoteLocalDAO() {
	}

	public SaldoLoteLocalDAO(Connection conn) {
		this.conn = conn;
	}

	private Connection getEffectiveConnection() throws SQLException {
		return (this.conn != null && !this.conn.isClosed()) ? this.conn : Conexao.getConexao();
	}

	// Calcula a soma do saldo do produto em um local específico
	public BigDecimal saldoTotal(int produtoId, int localId) throws SQLException {
		return saldoTotal(produtoId, localId, getEffectiveConnection());
	}

	public BigDecimal saldoTotal(int produtoId, int localId, Connection connection) throws SQLException {
		String sql = "SELECT COALESCE(SUM(s.quantidade), 0) FROM saldo_lote_local s "
				+ "JOIN lote l ON l.id = s.lote_id "
				+ "WHERE l.produto_id = ? AND s.local_id = ?";

		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setInt(1, produtoId);
			stmt.setInt(2, localId);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getBigDecimal(1);
				}
			}
		}
		return BigDecimal.ZERO;
	}

	public void somar(int loteId, int localId, BigDecimal qtd) throws SQLException {
		somar(loteId, localId, qtd, getEffectiveConnection());
	}

	public void somar(int loteId, int localId, BigDecimal qtd, Connection connection) throws SQLException {
		String sql = "INSERT INTO saldo_lote_local (lote_id, local_id, quantidade) VALUES (?, ?, ?) "
				+ "ON CONFLICT (lote_id, local_id) DO UPDATE SET quantidade = saldo_lote_local.quantidade + EXCLUDED.quantidade";

		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setInt(1, loteId);
			stmt.setInt(2, localId);
			stmt.setBigDecimal(3, qtd);
			stmt.executeUpdate();
		}
	}

	public List<SaldoLoteLocal> listarFifo(int produtoId, int localId) throws SQLException {
		return listarFifo(produtoId, localId, getEffectiveConnection());
	}

	public List<SaldoLoteLocal> listarFifo(int produtoId, int localId, Connection connection) throws SQLException {
		List<SaldoLoteLocal> lista = new ArrayList<>();
		String sql = "SELECT s.lote_id, s.local_id, s.quantidade FROM saldo_lote_local s "
				+ "JOIN lote l ON l.id = s.lote_id "
				+ "WHERE l.produto_id = ? AND s.local_id = ? AND s.quantidade > 0 "
				+ "ORDER BY l.data_validade ASC, l.id ASC FOR UPDATE";

		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setInt(1, produtoId);
			stmt.setInt(2, localId);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					lista.add(new SaldoLoteLocal(
						rs.getInt("lote_id"),
						rs.getInt("local_id"),
						rs.getBigDecimal("quantidade")
					));
				}
			}
		}
		return lista;
	}
}