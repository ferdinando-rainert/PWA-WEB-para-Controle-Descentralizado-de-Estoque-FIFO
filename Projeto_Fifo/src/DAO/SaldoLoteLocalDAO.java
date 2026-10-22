package DAO;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import Conexao.Conexao;
import entities.SaldoLoteLocal;

public class SaldoLoteLocalDAO {

	// Lista os saldos de um produto em um local, do lote MAIS ANTIGO para o mais novo (regra FIFO).
	// FOR UPDATE trava as linhas durante a transação para evitar saída duplicada.
	public List<SaldoLoteLocal> listarFifo(int produtoId, int localId) throws SQLException {

		List<SaldoLoteLocal> lista = new ArrayList<>();

		String sql = "SELECT s.lote_id, s.local_id, s.quantidade FROM saldo_lote_local s "
				+ "JOIN lote l ON l.id = s.lote_id "
				+ "WHERE l.produto_id = ? AND s.local_id = ? AND s.quantidade > 0 "
				+ "ORDER BY l.data_entrada ASC, l.id ASC FOR UPDATE";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(sql)) {

			stmt.setInt(1, produtoId);
			stmt.setInt(2, localId);

			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					lista.add(new SaldoLoteLocal(rs.getInt("lote_id"), rs.getInt("local_id"),
							rs.getBigDecimal("quantidade")));
				}
			}
		}
		return lista;
	}

	// Soma (delta positivo) ou subtrai (delta negativo) a quantidade de um lote em um local.
	public void somar(int loteId, int localId, BigDecimal delta) throws SQLException {

		String update = "UPDATE saldo_lote_local SET quantidade = quantidade + ? WHERE lote_id = ? AND local_id = ?";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(update)) {

			stmt.setBigDecimal(1, delta);
			stmt.setInt(2, loteId);
			stmt.setInt(3, localId);

			if (stmt.executeUpdate() > 0) {
				return;
			}
		}

		// Ainda não existe saldo desse lote nesse local: cria a linha
		if (delta.signum() < 0) {
			throw new SQLException("Saldo inexistente para o lote " + loteId + " no local " + localId);
		}

		String insert = "INSERT INTO saldo_lote_local (lote_id, local_id, quantidade) VALUES (?, ?, ?)";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(insert)) {
			stmt.setInt(1, loteId);
			stmt.setInt(2, localId);
			stmt.setBigDecimal(3, delta);
			stmt.executeUpdate();
		}
	}

	// Saldo total de um produto em um local (soma de todos os lotes)
	public BigDecimal saldoTotal(int produtoId, int localId) {

		String sql = "SELECT COALESCE(SUM(s.quantidade), 0) FROM saldo_lote_local s "
				+ "JOIN lote l ON l.id = s.lote_id WHERE l.produto_id = ? AND s.local_id = ?";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(sql)) {

			stmt.setInt(1, produtoId);
			stmt.setInt(2, localId);

			try (ResultSet rs = stmt.executeQuery()) {
				rs.next();
				return rs.getBigDecimal(1);
			}

		} catch (SQLException e) {
			System.err.println("Erro ao consultar o saldo: " + e.getMessage());
			return BigDecimal.ZERO;
		}
	}

}
