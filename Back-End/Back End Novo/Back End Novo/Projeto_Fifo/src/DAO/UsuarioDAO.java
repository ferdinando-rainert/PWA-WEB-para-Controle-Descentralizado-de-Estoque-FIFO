package DAO;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import Conexao.Conexao;
import entities.Perfil;
import entities.Usuario;

public class UsuarioDAO {

	// Método para cadastrar Usuário
	public void cadastrarUsuario(Usuario u) {

		String sql = "INSERT INTO usuario (nome, email, senha_hash, perfil) VALUES (?, ?, ?, ?)";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(sql)) {

			stmt.setString(1, u.getNome());
			stmt.setString(2, u.getEmail());
			stmt.setString(3, u.getSenhaHash());
			stmt.setString(4, u.getPerfil().name());

			stmt.executeUpdate();
			System.out.println("Usuário cadastrado!");

		} catch (SQLException e) {
			System.err.println("Erro ao cadastrar o Usuário: " + e.getMessage());
		}
	}

	// Método para buscar Usuário pelo ID (retorna null se não existir)
	public Usuario buscarPorId(int id) {

		String sql = "SELECT * FROM usuario WHERE id = ?";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(sql)) {

			stmt.setInt(1, id);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return mapear(rs);
				}
			}

		} catch (SQLException e) {
			System.err.println("Erro ao buscar o Usuário: " + e.getMessage());
		}
		return null;
	}

	// Método para listar todos os Usuários
	public List<Usuario> listarTodos() {

		List<Usuario> lista = new ArrayList<>();
		String sql = "SELECT * FROM usuario ORDER BY nome";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(sql);
				ResultSet rs = stmt.executeQuery()) {

			while (rs.next()) {
				lista.add(mapear(rs));
			}

		} catch (SQLException e) {
			System.err.println("Erro ao listar os Usuários: " + e.getMessage());
		}
		return lista;
	}

	private Usuario mapear(ResultSet rs) throws SQLException {
		Usuario u = new Usuario(rs.getString("nome"), rs.getString("email"), rs.getString("senha_hash"),
				Perfil.valueOf(rs.getString("perfil")));
		u.setId(rs.getInt("id"));
		u.setAtivo(rs.getBoolean("ativo"));
		return u;
	}

}
