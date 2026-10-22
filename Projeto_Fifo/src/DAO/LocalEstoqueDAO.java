package DAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import Conexao.Conexao;
import entities.LocalEstoque;

public class LocalEstoqueDAO {

	private Connection conn;

	public LocalEstoqueDAO() {
	}

	public LocalEstoqueDAO(Connection conn) {
		this.conn = conn;
	}

	private Connection getEffectiveConnection() throws SQLException {
		return (this.conn != null && !this.conn.isClosed()) ? this.conn : Conexao.getConexao();
	}

	public void cadastrarLocal(LocalEstoque local) throws SQLException {
		cadastrarLocal(local, getEffectiveConnection());
	}

	public void cadastrarLocal(LocalEstoque local, Connection connection) throws SQLException {
		String sql = "INSERT INTO local_estoque (nome, descricao) VALUES (?, ?)";
		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setString(1, local.getNome());
			stmt.setString(2, local.getDescricao());
			stmt.executeUpdate();
		}
	}

	public LocalEstoque buscarPorId(int id) throws SQLException {
		return buscarPorId(id, getEffectiveConnection());
	}

	public LocalEstoque buscarPorId(int id, Connection connection) throws SQLException {
		String sql = "SELECT * FROM local_estoque WHERE id = ?";
		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setInt(1, id);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					LocalEstoque local = new LocalEstoque();
					local.setId(rs.getInt("id"));
					local.setNome(rs.getString("nome"));
					local.setDescricao(rs.getString("descricao"));
					local.setAtivo(rs.getBoolean("ativo"));
					return local;
				}
			}
		}
		return null;
	}

	public List<LocalEstoque> listarTodos() throws SQLException {
		return listarTodos(getEffectiveConnection());
	}

	public List<LocalEstoque> listarTodos(Connection connection) throws SQLException {
		List<LocalEstoque> lista = new ArrayList<>();
		String sql = "SELECT * FROM local_estoque ORDER BY nome";

		try (PreparedStatement stmt = connection.prepareStatement(sql);
				ResultSet rs = stmt.executeQuery()) {

			while (rs.next()) {
				LocalEstoque local = new LocalEstoque();
				local.setId(rs.getInt("id"));
				local.setNome(rs.getString("nome"));
				local.setDescricao(rs.getString("descricao"));
				local.setAtivo(rs.getBoolean("ativo"));
				lista.add(local);
			}
		}
		return lista;
	}
}