package DAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import Conexao.Conexao;
import entities.Lote;

public class LoteDAO {

	private Connection conn;

	public LoteDAO() {}

	public LoteDAO(Connection conn) {
		this.conn = conn;
	}

	private Connection getEffectiveConnection() throws SQLException {
		return (this.conn != null && !this.conn.isClosed()) ? this.conn : Conexao.getConexao();
	}

	public int cadastrarLote(Lote lote) throws SQLException {
		return cadastrarLote(lote, getEffectiveConnection());
	}

	public int cadastrarLote(Lote lote, Connection connection) throws SQLException {
		String sql = "INSERT INTO lote (produto_id, codigo_lote, data_validade, quantidade_inicial) VALUES (?, ?, ?, ?) RETURNING id";
		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setInt(1, lote.getProdutoId());
			stmt.setString(2, lote.getCodigoLote());
			stmt.setObject(3, lote.getDataValidade());
			stmt.setBigDecimal(4, lote.getQuantidadeInicial());
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getInt(1);
				}
			}
		}
		return 0;
	}

	public Lote buscarPorId(int id) throws SQLException {
		return buscarPorId(id, getEffectiveConnection());
	}

	public Lote buscarPorId(int id, Connection connection) throws SQLException {
		String sql = "SELECT * FROM lote WHERE id = ?";
		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setInt(1, id);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					Lote l = new Lote(
						rs.getInt("produto_id"),
						rs.getString("codigo_lote"),
						rs.getDate("data_validade") != null ? rs.getDate("data_validade").toLocalDate() : null,
						rs.getBigDecimal("quantidade_inicial")
					);
					l.setId(rs.getInt("id"));
					return l;
				}
			}
		}
		return null;
	}
}