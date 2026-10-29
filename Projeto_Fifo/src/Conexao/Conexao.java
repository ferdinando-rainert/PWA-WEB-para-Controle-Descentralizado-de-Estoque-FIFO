		package Conexao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Conexao {

	private static final String URL = System.getenv().getOrDefault("FIFO_DB_URL",
			"jdbc:mysql://localhost:3305/estoque_fifo?serverTimezone=UTC&useSSL=false");
	private static final String USUARIO = System.getenv().getOrDefault("FIFO_DB_USER", "root");
	private static final String SENHA = System.getenv().getOrDefault("FIFO_DB_PASSWORD", "");

	public static Connection getConexao() throws SQLException {
		return DriverManager.getConnection(URL, USUARIO, SENHA);
	}

	public static void main(String[] args) {
		try (Connection conexao = getConexao()) {
			System.out.println("Conectado ao banco estoque_fifo!");
			System.out.println("Banco: " +
					conexao.getCatalog());
			System.out.println("Conexão ativa: " +
					!conexao.isClosed());

		} catch (SQLException e) {
			System.err.println("Falha ao conectar ao MySQL!");
			System.err.println("Motivo: " + e.getMessage());
		}
	}
}

