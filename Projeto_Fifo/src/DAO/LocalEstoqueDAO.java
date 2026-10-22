package DAO;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import Conexao.Conexao;
import entities.LocalEstoque;

public class LocalEstoqueDAO {

	// Método para cadastrar Local de Estoque
	public void cadastrarLocal(LocalEstoque local) {

		String sql = "INSERT INTO local_estoque (nome, descricao) VALUES (?, ?)";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(sql)) {

			stmt.setString(1, local.getNome());
			stmt.setString(2, local.getDescricao());

			stmt.executeUpdate();
			System.out.println("Local de estoque cadastrado!");

		} catch (SQLException e) {
			System.err.println("Erro ao cadastrar o Local: " + e.getMessage());
		}
	}

	// Método para buscar Local pelo ID (retorna null se não existir)
	public LocalEstoque buscarPorId(int id) {

		String sql = "SELECT * FROM local_estoque WHERE id = ?";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(sql)) {

			stmt.setInt(1, id);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return mapear(rs);
				}
			}

		} catch (SQLException e) {
			System.err.println("Erro ao buscar o Local: " + e.getMessage());
		}
		return null;
	}

	// Método para listar todos os Locais
	public List<LocalEstoque> listarTodos() {

		List<LocalEstoque> lista = new ArrayList<>();
		String sql = "SELECT * FROM local_estoque ORDER BY nome";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(sql);
				ResultSet rs = stmt.executeQuery()) {

			while (rs.next()) {
				lista.add(mapear(rs));
			}

		} catch (SQLException e) {
			System.err.println("Erro ao listar os Locais: " + e.getMessage());
		}
		return lista;
	}

	private LocalEstoque mapear(ResultSet rs) throws SQLException {
		LocalEstoque l = new LocalEstoque(rs.getString("nome"), rs.getString("descricao"));
		l.setId(rs.getInt("id"));
		l.setAtivo(rs.getBoolean("ativo"));
		return l;
	}

}
