package application;

import java.io.IOException;
import java.io.Console;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import Conexao.Conexao;
import DAO.MovimentacaoDAO;
import entities.Perfil;
import service.EstoqueService;

public final class ApiServer {

	private static final String API_PREFIX = "/api/v1";
	private static final int MAX_REQUEST_BYTES = 1_048_576;
	private static final int PASSWORD_ITERATIONS = 210_000;
	private static final long SESSION_HOURS = 12;
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String SECURE_SESSION_COOKIE = "__Host-fifo_session";
	private static final String LOCAL_SESSION_COOKIE = "fifo_session";
	private static final Set<String> DEFAULT_ORIGINS = Set.of(
			"http://127.0.0.1:5500",
			"http://localhost:5500");
	private static final Map<String, LoginAttempt> LOGIN_ATTEMPTS = new ConcurrentHashMap<>();

	private final EstoqueService estoqueService = new EstoqueService();
	private final Set<String> allowedOrigins;
	private final String proxySecret;
	private final boolean secureCookies;

	private ApiServer() {
		String configuredOrigins = System.getenv("FIFO_API_CORS_ORIGINS");
		if (configuredOrigins == null || configuredOrigins.isBlank()) {
			allowedOrigins = DEFAULT_ORIGINS;
		} else {
			allowedOrigins = configuredOrigins.lines()
					.flatMap(line -> List.of(line.split(",")).stream())
					.map(String::trim)
					.filter(origin -> !origin.isEmpty())
					.collect(java.util.stream.Collectors.toUnmodifiableSet());
		}
		proxySecret = System.getenv("FIFO_API_PROXY_SECRET");
		secureCookies = Boolean.parseBoolean(System.getenv().getOrDefault("FIFO_COOKIE_SECURE", "true"));
	}

	public static void main(String[] args) throws IOException, SQLException {
		if (args.length > 0) {
			if (args.length == 1 && "create-admin".equals(args[0])) {
				createInitialAdmin();
				return;
			}
			if (args.length == 2 && "reset-password".equals(args[0])) {
				resetPassword(args[1]);
				return;
			}
			throw new IllegalArgumentException("Uso: ApiServer [create-admin | reset-password email]");
		}
		String host = System.getenv().getOrDefault("FIFO_API_HOST", "127.0.0.1");
		int port = Integer.parseInt(System.getenv().getOrDefault("FIFO_API_PORT", "8080"));
		boolean behindProxy = System.getenv("FIFO_API_PROXY_SECRET") != null
				&& !System.getenv("FIFO_API_PROXY_SECRET").isBlank();
		if (!isLoopback(host) && !behindProxy) {
			throw new IllegalArgumentException("Uma API exposta na rede exige FIFO_API_PROXY_SECRET.");
		}
		if (behindProxy && !Boolean.parseBoolean(System.getenv().getOrDefault("FIFO_COOKIE_SECURE", "true"))) {
			throw new IllegalArgumentException("Cookies Secure são obrigatórios quando a API usa um proxy remoto.");
		}

		verifyDatabase();
		if (behindProxy && !isStrongSecret(System.getenv("FIFO_API_PROXY_SECRET"))) {
			throw new IllegalArgumentException("FIFO_API_PROXY_SECRET deve conter pelo menos 32 caracteres.");
		}
		ApiServer application = new ApiServer();
		HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
		server.createContext(API_PREFIX, application::handle);
		ExecutorService executor = Executors.newFixedThreadPool(
				Math.max(4, Runtime.getRuntime().availableProcessors()));
		server.setExecutor(executor);
		server.start();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			server.stop(1);
			executor.shutdown();
		}, "fifo-api-shutdown"));
		System.out.printf("API Estoque FIFO disponível em http://%s:%d%s%n", host, port, API_PREFIX);
		System.out.println("CORS permitido para: " + application.allowedOrigins);
	}

	private static void resetPassword(String email) throws SQLException {
		char[] password = System.console() == null ? null : System.console().readPassword("Nova senha (mínimo 12 caracteres): ");
		if (password == null) {
			throw new IllegalStateException("Execute este comando em um terminal interativo para não expor a senha.");
		}
		try {
			if (password.length < 12 || password.length > 128) {
				throw new IllegalArgumentException("A senha deve ter entre 12 e 128 caracteres.");
			}
			try (Connection connection = Conexao.getConexao();
					PreparedStatement statement = connection.prepareStatement(
							"UPDATE usuario SET senha_hash = ? WHERE LOWER(email) = LOWER(?) AND ativo = TRUE")) {
				statement.setString(1, hashPassword(new String(password)));
				statement.setString(2, email);
				if (statement.executeUpdate() != 1) {
					throw new IllegalArgumentException("Informe o email de um único usuário ativo existente.");
				}
			}
			System.out.println("Senha atualizada. Remova qualquer variável temporária usada no deploy.");
		} finally {
			java.util.Arrays.fill(password, '\0');
		}
	}

	private static void createInitialAdmin() throws SQLException {
			Console console = System.console();
			if (console == null) {
				throw new IllegalStateException("Execute este comando em um terminal interativo.");
			}
			String name = console.readLine("Nome do administrador: ").trim();
			String email = console.readLine("Email do administrador: ").trim().toLowerCase(Locale.ROOT);
			char[] password = console.readPassword("Senha (mínimo 12 caracteres): ");
			char[] confirmation = console.readPassword("Confirme a senha: ");
			try {
				if (password == null || confirmation == null || name.isEmpty() || name.length() > 160
						|| !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
						|| password.length < 12 || password.length > 128
						|| !java.util.Arrays.equals(password, confirmation)) {
					throw new IllegalArgumentException("Nome, email ou senha inválidos; confirme uma senha de 12 a 128 caracteres.");
				}
				try (Connection connection = Conexao.getConexao()) {
					try (Statement lock = connection.createStatement();
							ResultSet result = lock.executeQuery("SELECT GET_LOCK('estoque_fifo_initial_admin', 10)")) {
						if (!result.next() || result.getInt(1) != 1) {
							throw new SQLException("Não foi possível bloquear a criação do administrador inicial.");
						}
					}
					try {
						try (Statement statement = connection.createStatement();
								ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM usuario")) {
							result.next();
							if (result.getLong(1) != 0) {
								throw new IllegalStateException("Já existem usuários. Use reset-password para uma conta existente.");
							}
						}
						try (PreparedStatement statement = connection.prepareStatement(
								"INSERT INTO usuario (nome, email, senha_hash, perfil, ativo) VALUES (?, ?, ?, 'ADMIN', TRUE)")) {
							statement.setString(1, name);
							statement.setString(2, email);
							statement.setString(3, hashPassword(new String(password)));
							statement.executeUpdate();
						}
					} finally {
						try (Statement unlock = connection.createStatement()) {
							unlock.execute("SELECT RELEASE_LOCK('estoque_fifo_initial_admin')");
						}
					}
				}
				System.out.println("Administrador inicial criado. A senha não foi exibida nem armazenada em texto puro.");
			} finally {
				if (password != null) java.util.Arrays.fill(password, '\0');
				if (confirmation != null) java.util.Arrays.fill(confirmation, '\0');
			}
	}

	private static boolean isStrongSecret(String secret) {
		return secret != null && secret.length() >= 32;
	}

	private static boolean isLoopback(String host) {
		try {
			return InetSocketAddress.createUnresolved(host, 0).getHostString().equalsIgnoreCase("localhost")
					|| java.net.InetAddress.getByName(host).isLoopbackAddress();
		} catch (IOException exception) {
			return false;
		}
	}

	private static void verifyDatabase() throws SQLException {
		try (Connection connection = Conexao.getConexao()) {
			try (ResultSet tables = connection.getMetaData().getTables(
					connection.getCatalog(), connection.getCatalog(), "sessao_usuario", new String[] { "TABLE" })) {
				if (!tables.next()) {
					throw new SQLException("Tabela sessao_usuario ausente. Execute "
							+ "Projeto_Fifo/db/migration-auth-session.sql antes de iniciar a API.");
				}
			}
			boolean hasUniqueUuid = false;
			try (ResultSet indexes = connection.getMetaData().getIndexInfo(
					connection.getCatalog(), connection.getCatalog(), "movimentacao", true, false)) {
				while (indexes.next()) {
					String column = indexes.getString("COLUMN_NAME");
					if (!indexes.getBoolean("NON_UNIQUE") && column != null
							&& column.equalsIgnoreCase("uuid_cliente")) {
						hasUniqueUuid = true;
						break;
					}
				}
			}
			if (!hasUniqueUuid) {
				throw new SQLException("A tabela movimentacao precisa do índice UNIQUE em uuid_cliente. "
						+ "Execute Projeto_Fifo/db/schema-mysql.sql antes de iniciar a API.");
			}
		}
	}

	private void handle(HttpExchange exchange) throws IOException {
		try {
			verifyProxySecret(exchange);
			if (!setCors(exchange)) {
				sendError(exchange, 403, "Origem não autorizada pelo CORS.");
				return;
			}
			if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
				exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
				exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Accept, Content-Type, X-CSRF-Token");
				send(exchange, 204, null);
				return;
			}

			String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
			String path = exchange.getRequestURI().getPath().substring(API_PREFIX.length());
			if (path.isEmpty()) {
				path = "/";
			}
			Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
			ApiSession session = null;
			if (!"/auth/login".equals(path) && !"/health".equals(path)) {
				session = requireSession(exchange);
				if (Set.of("POST", "PUT", "PATCH", "DELETE").contains(method)) {
					requireCsrf(exchange, session);
				}
			}
			Object result = dispatch(exchange, method, path, query, session);
			send(exchange, 200, result);
		} catch (HttpFailure failure) {
			sendError(exchange, failure.status, failure.getMessage());
		} catch (IllegalArgumentException exception) {
			sendError(exchange, 400, safeMessage(exception));
		} catch (SQLException exception) {
			System.err.printf("Erro SQL na API (estado %s, código %d): %s%n",
					exception.getSQLState(), exception.getErrorCode(), exception.getMessage());
			if (isDuplicate(exception)) {
				sendError(exchange, 409, "Já existe um registro com esses dados.");
			} else {
				sendError(exchange, 500, "Falha ao acessar o banco de dados.");
			}
		} catch (Exception exception) {
			System.err.println("Erro inesperado na API: " + exception.getMessage());
			sendError(exchange, 500, "Erro interno do servidor.");
		} finally {
			exchange.close();
		}
	}

	private void verifyProxySecret(HttpExchange exchange) {
		if (proxySecret == null || proxySecret.isBlank()) return;
		String supplied = exchange.getRequestHeaders().getFirst("X-Fifo-Proxy-Secret");
		if (supplied == null || !MessageDigest.isEqual(
				proxySecret.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
			throw new HttpFailure(403, "Requisição não autorizada.");
		}
	}

	private boolean setCors(HttpExchange exchange) {
		Headers headers = exchange.getResponseHeaders();
		headers.set("Content-Type", "application/json; charset=utf-8");
		headers.set("Cache-Control", "no-store");
		headers.set("X-Content-Type-Options", "nosniff");
		headers.set("Referrer-Policy", "no-referrer");
		String origin = exchange.getRequestHeaders().getFirst("Origin");
		if (origin == null || origin.isBlank()) {
			return true;
		}
		if (!allowedOrigins.contains(origin)) {
			return false;
		}
		headers.set("Access-Control-Allow-Origin", origin);
		headers.set("Access-Control-Allow-Credentials", "true");
		headers.set("Vary", "Origin");
		return true;
	}

	private Object dispatch(HttpExchange exchange, String method, String path, Map<String, String> query,
			ApiSession session) throws Exception {
		if ("/auth/login".equals(path) && "POST".equals(method)) return login(exchange);
		if ("/auth/session".equals(path) && "GET".equals(method)) {
			return map("id", session.userId, "nome", session.name, "email", session.email,
					"perfil", session.profile, "csrfToken", session.csrfToken);
		}
		if ("/auth/logout".equals(path) && "POST".equals(method)) {
			logout(exchange);
			return map("message", "Sessão encerrada.");
		}
		if ("/health".equals(path) && "GET".equals(method)) {
			try (Connection connection = Conexao.getConexao();
					Statement statement = connection.createStatement();
					ResultSet rs = statement.executeQuery("SELECT 1")) {
				rs.next();
				return Map.of("status", "ok", "database", "connected");
			}
		}
		if ("/produtos".equals(path)) {
			if ("GET".equals(method)) return listProducts();
			if ("POST".equals(method)) {
				requireRole(session, "ADMIN", "GESTOR");
				return createProduct(readObject(exchange));
			}
		}
		if ("/locais".equals(path)) {
			if ("GET".equals(method)) return listLocations();
			if ("POST".equals(method)) {
				requireRole(session, "ADMIN", "GESTOR");
				return createLocation(readObject(exchange));
			}
		}
		if ("/usuarios".equals(path)) {
			if ("GET".equals(method)) return listUsers(session);
			if ("POST".equals(method)) {
				requireRole(session, "ADMIN");
				return createUser(readObject(exchange));
			}
		}
		if ("/lotes".equals(path) && "GET".equals(method)) return listLots();
		if ("/estoque/saldos".equals(path) && "GET".equals(method)) return listBalances();
		if ("/movimentacoes".equals(path) && "GET".equals(method)) {
			return listMovements(parseLimit(query.get("limite")));
		}
		if ("/estoque/entradas".equals(path) && "POST".equals(method)) {
			return registerEntry(readObject(exchange), session);
		}
		if ("/estoque/saidas".equals(path) && "POST".equals(method)) {
			return registerExit(readObject(exchange), session);
		}
		if ("/estoque/transferencias".equals(path) && "POST".equals(method)) {
			return registerTransfer(readObject(exchange), session);
		}

		String[] segments = path.split("/");
		if (segments.length == 3 && !segments[2].isBlank()) {
			boolean supportedResource = "produtos".equals(segments[1]) || "locais".equals(segments[1])
					|| "usuarios".equals(segments[1]);
			if (supportedResource && "PUT".equals(method)) {
				if ("usuarios".equals(segments[1])) requireRole(session, "ADMIN");
				else requireRole(session, "ADMIN", "GESTOR");
				int id = parseId(segments[2]);
				Map<String, Object> body = readObject(exchange);
				if ("produtos".equals(segments[1])) return updateProduct(id, body);
				if ("locais".equals(segments[1])) return updateLocation(id, body);
				return updateUser(id, body);
			}
		}
		throw new HttpFailure(404, "Rota não encontrada.");
	}

	private Map<String, Object> login(HttpExchange exchange) throws SQLException, IOException {
		String origin = exchange.getRequestHeaders().getFirst("Origin");
		if (origin != null && !isAllowedOrigin(origin)) {
			throw new HttpFailure(403, "Origem da requisição não autorizada.");
		}
		Map<String, Object> body = readObject(exchange);
		String email = email(body);
		String password = secret(body, "senha");
		String remoteAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
		String attemptKey = remoteAddress + "|" + email;
		LoginAttempt attempt = LOGIN_ATTEMPTS.computeIfAbsent(attemptKey, ignored -> new LoginAttempt());
		attempt.checkAllowed();

		try (Connection connection = Conexao.getConexao();
				PreparedStatement statement = connection.prepareStatement(
						"SELECT id, nome, email, perfil, senha_hash FROM usuario "
								+ "WHERE LOWER(email) = LOWER(?) AND ativo = TRUE")) {
			statement.setString(1, email);
			try (ResultSet rs = statement.executeQuery()) {
				if (!rs.next() || !verifyPassword(password, rs.getString("senha_hash"))) {
					attempt.recordFailure();
					throw new HttpFailure(401, "Email ou senha inválidos.");
				}
				attempt.reset();
				int userId = rs.getInt("id");
				String csrfToken = randomToken(32);
				String rawSessionToken = randomToken(32);
				String tokenHash = sha256(rawSessionToken);
				try (PreparedStatement cleanup = connection.prepareStatement(
						"DELETE FROM sessao_usuario WHERE usuario_id = ? OR expira_em <= CURRENT_TIMESTAMP")) {
					cleanup.setInt(1, userId);
					cleanup.executeUpdate();
				}
				try (PreparedStatement insert = connection.prepareStatement(
						"INSERT INTO sessao_usuario (token_hash, usuario_id, csrf_token, expira_em) "
								+ "VALUES (?, ?, ?, ?)")) {
					insert.setString(1, tokenHash);
					insert.setInt(2, userId);
					insert.setString(3, csrfToken);
					insert.setTimestamp(4, java.sql.Timestamp.valueOf(
							java.time.LocalDateTime.now().plusHours(SESSION_HOURS)));
					insert.executeUpdate();
				}
				exchange.getResponseHeaders().add("Set-Cookie", sessionCookie(rawSessionToken, SESSION_HOURS * 3600));
				return map("id", userId, "nome", rs.getString("nome"), "email", rs.getString("email"),
						"perfil", rs.getString("perfil"), "csrfToken", csrfToken);
			}
		}
	}

	private ApiSession requireSession(HttpExchange exchange) throws SQLException {
		String token = cookie(exchange, sessionCookieName());
		if (token == null || token.length() < 40 || token.length() > 100) {
			throw new HttpFailure(401, "Faça login para continuar.");
		}
		try (Connection connection = Conexao.getConexao();
				PreparedStatement statement = connection.prepareStatement(
						"SELECT s.usuario_id, s.csrf_token, u.nome, u.email, u.perfil "
								+ "FROM sessao_usuario s JOIN usuario u ON u.id = s.usuario_id "
								+ "WHERE s.token_hash = ? AND s.expira_em > CURRENT_TIMESTAMP AND u.ativo = TRUE")) {
			statement.setString(1, sha256(token));
			try (ResultSet rs = statement.executeQuery()) {
				if (!rs.next()) {
					throw new HttpFailure(401, "Sessão expirada. Entre novamente.");
				}
				return new ApiSession(rs.getInt("usuario_id"), rs.getString("nome"),
						rs.getString("email"), rs.getString("perfil"), rs.getString("csrf_token"));
			}
		}
	}

	private void requireCsrf(HttpExchange exchange, ApiSession session) {
		String origin = exchange.getRequestHeaders().getFirst("Origin");
		String referer = exchange.getRequestHeaders().getFirst("Referer");
		boolean originAllowed = origin != null ? isAllowedOrigin(origin)
				: referer != null && isAllowedOrigin(originOf(referer));
		if (!originAllowed) {
			throw new HttpFailure(403, "Origem da requisição não autorizada.");
		}
		String csrf = exchange.getRequestHeaders().getFirst("X-CSRF-Token");
		if (csrf == null || !MessageDigest.isEqual(
				session.csrfToken.getBytes(StandardCharsets.UTF_8), csrf.getBytes(StandardCharsets.UTF_8))) {
			throw new HttpFailure(403, "Token de segurança inválido. Atualize a página e tente novamente.");
		}
	}

	private boolean isAllowedOrigin(String origin) {
		if (origin == null || origin.isBlank()) return false;
		if (allowedOrigins.contains(origin)) return true;
		try {
			URI uri = URI.create(origin);
			return uri.getScheme() != null && uri.getHost() != null
					&& "https".equalsIgnoreCase(uri.getScheme())
					&& "vercel.app".equalsIgnoreCase(uri.getHost())
					&& allowedOrigins.contains("https://" + uri.getHost());
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private static String originOf(String referer) {
		try {
			URI uri = URI.create(referer);
			if (uri.getScheme() == null || uri.getHost() == null) return null;
			return uri.getScheme() + "://" + uri.getAuthority();
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private void logout(HttpExchange exchange) throws SQLException {
		String token = cookie(exchange, sessionCookieName());
		if (token != null) {
			try (Connection connection = Conexao.getConexao();
					PreparedStatement statement = connection.prepareStatement(
							"DELETE FROM sessao_usuario WHERE token_hash = ?")) {
				statement.setString(1, sha256(token));
				statement.executeUpdate();
			}
		}
		exchange.getResponseHeaders().add("Set-Cookie", sessionCookie("", 0));
	}

	private String sessionCookie(String token, long maxAge) {
		return sessionCookieName() + "=" + token + "; Path=/; Max-Age=" + maxAge
				+ "; HttpOnly; SameSite=Lax" + (secureCookies ? "; Secure" : "");
	}

	private String sessionCookieName() {
		return secureCookies ? SECURE_SESSION_COOKIE : LOCAL_SESSION_COOKIE;
	}

	private static String cookie(HttpExchange exchange, String name) {
		List<String> cookies = exchange.getRequestHeaders().get("Cookie");
		if (cookies == null) return null;
		for (String header : cookies) {
			for (String entry : header.split(";")) {
				String[] pair = entry.trim().split("=", 2);
				if (pair.length == 2 && name.equals(pair[0])) return pair[1];
			}
		}
		return null;
	}

	private static void requireRole(ApiSession session, String... allowedRoles) {
		for (String role : allowedRoles) {
			if (role.equals(session.profile)) return;
		}
		throw new HttpFailure(403, "Seu perfil não tem permissão para esta operação.");
	}

	private static boolean verifyPassword(String password, String encoded) {
		if (encoded == null) return false;
		String[] parts = encoded.split("\\$", -1);
		if (parts.length != 5 || !"pbkdf2-sha256".equals(parts[1])) return false;
		try {
			int iterations = Integer.parseInt(parts[2]);
			if (iterations < 100_000 || iterations > 1_000_000) return false;
			Base64.Decoder decoder = Base64.getUrlDecoder();
			byte[] salt = decoder.decode(parts[3]);
			byte[] expected = decoder.decode(parts[4]);
			PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, expected.length * 8);
			try {
				byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
						.generateSecret(spec).getEncoded();
				return MessageDigest.isEqual(expected, actual);
			} finally {
				spec.clearPassword();
			}
		} catch (IllegalArgumentException | java.security.GeneralSecurityException exception) {
			return false;
		}
	}

	private static String randomToken(int byteCount) {
		byte[] bytes = new byte[byteCount];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte b : digest) hex.append(String.format("%02x", b));
			return hex.toString();
		} catch (java.security.GeneralSecurityException exception) {
			throw new IllegalStateException("SHA-256 indisponível.", exception);
		}
	}

	private List<Map<String, Object>> listProducts() throws SQLException {
		return query("SELECT id, sku, nome, unidade_medida, estoque_minimo, ativo FROM produto ORDER BY nome",
				rs -> map("id", rs.getInt("id"), "sku", rs.getString("sku"), "nome", rs.getString("nome"),
						"unidadeMedida", rs.getString("unidade_medida"),
						"estoqueMinimo", rs.getBigDecimal("estoque_minimo"), "ativo", rs.getBoolean("ativo")));
	}

	private List<Map<String, Object>> listLocations() throws SQLException {
		return query("SELECT id, nome, descricao, ativo FROM local_estoque ORDER BY nome",
				rs -> map("id", rs.getInt("id"), "nome", rs.getString("nome"),
						"descricao", rs.getString("descricao"), "ativo", rs.getBoolean("ativo")));
	}

	private List<Map<String, Object>> listUsers(ApiSession session) throws SQLException {
		if (!Set.of("ADMIN", "GESTOR").contains(session.profile)) {
			return query("SELECT id, nome, email, perfil, ativo FROM usuario WHERE id = ?",
					rs -> map("id", rs.getInt("id"), "nome", rs.getString("nome"),
							"email", rs.getString("email"), "perfil", rs.getString("perfil"),
							"ativo", rs.getBoolean("ativo")), session.userId);
		}
		return query("SELECT id, nome, email, perfil, ativo FROM usuario ORDER BY nome",
				rs -> map("id", rs.getInt("id"), "nome", rs.getString("nome"), "email", rs.getString("email"),
						"perfil", rs.getString("perfil"), "ativo", rs.getBoolean("ativo")));
	}

	private List<Map<String, Object>> listLots() throws SQLException {
		return query("SELECT id, produto_id, codigo_lote, data_entrada, data_validade, quantidade_inicial "
				+ "FROM lote ORDER BY data_entrada, id", rs -> map("id", rs.getInt("id"),
						"produtoId", rs.getInt("produto_id"), "codigoLote", rs.getString("codigo_lote"),
						"dataEntrada", rs.getTimestamp("data_entrada") == null ? null
								: rs.getTimestamp("data_entrada").toLocalDateTime(),
						"dataValidade", rs.getDate("data_validade") == null ? null
								: rs.getDate("data_validade").toLocalDate(),
						"quantidadeInicial", rs.getBigDecimal("quantidade_inicial")));
	}

	private List<Map<String, Object>> listBalances() throws SQLException {
		return query("SELECT lote_id, local_id, quantidade FROM saldo_lote_local ORDER BY lote_id, local_id",
				rs -> map("loteId", rs.getInt("lote_id"), "localId", rs.getInt("local_id"),
						"quantidade", rs.getBigDecimal("quantidade")));
	}

	private List<Map<String, Object>> listMovements(int limit) throws SQLException {
		return query("SELECT id, uuid_cliente, tipo, lote_id, local_origem_id, local_destino_id, quantidade, "
				+ "usuario_id, data_hora_cliente, fifo_ignorado, justificativa FROM movimentacao "
				+ "ORDER BY id DESC LIMIT ?", rs -> map("id", rs.getLong("id"),
						"uuidCliente", rs.getString("uuid_cliente"), "tipo", rs.getString("tipo"),
						"loteId", rs.getInt("lote_id"), "localOrigemId", rs.getObject("local_origem_id"),
						"localDestinoId", rs.getObject("local_destino_id"),
						"quantidade", rs.getBigDecimal("quantidade"), "usuarioId", rs.getInt("usuario_id"),
						"dataHoraCliente", rs.getTimestamp("data_hora_cliente") == null ? null
								: rs.getTimestamp("data_hora_cliente").toLocalDateTime(),
						"fifoIgnorado", rs.getBoolean("fifo_ignorado"),
						"justificativa", rs.getString("justificativa")), limit);
	}

	private Map<String, Object> createProduct(Map<String, Object> body) throws SQLException {
		String sql = "INSERT INTO produto (sku, nome, unidade_medida, estoque_minimo, ativo) VALUES (?, ?, ?, ?, ?)";
		return insert("produto", sql, stmt -> {
			stmt.setString(1, text(body, "sku"));
			stmt.setString(2, text(body, "nome"));
			stmt.setString(3, text(body, "unidadeMedida"));
			stmt.setBigDecimal(4, decimal(body, "estoqueMinimo", true));
			stmt.setBoolean(5, bool(body, "ativo", true));
		});
	}

	private Map<String, Object> updateProduct(int id, Map<String, Object> body) throws SQLException {
		String sql = "UPDATE produto SET sku = ?, nome = ?, unidade_medida = ?, estoque_minimo = ?, ativo = ? WHERE id = ?";
		return update("produto", "produto", id, sql, stmt -> {
			stmt.setString(1, text(body, "sku"));
			stmt.setString(2, text(body, "nome"));
			stmt.setString(3, text(body, "unidadeMedida"));
			stmt.setBigDecimal(4, decimal(body, "estoqueMinimo", true));
			stmt.setBoolean(5, bool(body, "ativo", true));
			stmt.setInt(6, id);
		});
	}

	private Map<String, Object> createLocation(Map<String, Object> body) throws SQLException {
		return insert("local", "INSERT INTO local_estoque (nome, descricao, ativo) VALUES (?, ?, ?)", stmt -> {
			stmt.setString(1, text(body, "nome"));
			stmt.setString(2, optionalText(body, "descricao"));
			stmt.setBoolean(3, bool(body, "ativo", true));
		});
	}

	private Map<String, Object> updateLocation(int id, Map<String, Object> body) throws SQLException {
		return update("local_estoque", "local", id,
				"UPDATE local_estoque SET nome = ?, descricao = ?, ativo = ? WHERE id = ?", stmt -> {
					stmt.setString(1, text(body, "nome"));
					stmt.setString(2, optionalText(body, "descricao"));
					stmt.setBoolean(3, bool(body, "ativo", true));
					stmt.setInt(4, id);
				});
	}

	private Map<String, Object> createUser(Map<String, Object> body) throws SQLException {
		String password = secret(body, "senha");
		if (password.length() < 12) {
			throw new IllegalArgumentException("A senha deve ter no mínimo 12 caracteres.");
		}
		return insert("usuário",
				"INSERT INTO usuario (nome, email, senha_hash, perfil, ativo) VALUES (?, ?, ?, ?, ?)", stmt -> {
					stmt.setString(1, text(body, "nome"));
					stmt.setString(2, email(body));
					stmt.setString(3, hashPassword(password));
					stmt.setString(4, profile(body));
					stmt.setBoolean(5, bool(body, "ativo", true));
				});
	}

	private Map<String, Object> updateUser(int id, Map<String, Object> body) throws SQLException {
		String password = optionalSecret(body, "senha");
		if (!password.isEmpty() && password.length() < 12) {
			throw new IllegalArgumentException("A senha deve ter no mínimo 12 caracteres.");
		}
		String sql = password.isEmpty()
				? "UPDATE usuario SET nome = ?, email = ?, perfil = ?, ativo = ? WHERE id = ?"
				: "UPDATE usuario SET nome = ?, email = ?, perfil = ?, ativo = ?, senha_hash = ? WHERE id = ?";
		return update("usuario", "usuário", id, sql, stmt -> {
			stmt.setString(1, text(body, "nome"));
			stmt.setString(2, email(body));
			stmt.setString(3, profile(body));
			stmt.setBoolean(4, bool(body, "ativo", true));
			if (password.isEmpty()) {
				stmt.setInt(5, id);
			} else {
				stmt.setString(5, hashPassword(password));
				stmt.setInt(6, id);
			}
		});
	}

	private Map<String, Object> registerEntry(Map<String, Object> body, ApiSession session) throws SQLException {
		String uuid = uuid(body);
		Long existing = findMovement(uuid);
		if (existing != null) return idempotent(existing);
		try {
			estoqueService.registrarEntrada(positiveInt(body, "produtoId"), positiveInt(body, "localId"),
					text(body, "codigoLote"), date(body, "dataValidade"), quantity(body), session.userId,
					uuid);
		} catch (SQLException exception) {
			Long duplicate = isDuplicate(exception) ? findMovement(uuid) : null;
			if (duplicate != null) return idempotent(duplicate);
			throw exception;
		}
		return map("message", "Entrada registrada com sucesso.");
	}

	private Map<String, Object> registerExit(Map<String, Object> body, ApiSession session) throws SQLException {
		String uuid = uuid(body);
		Long existing = findMovement(uuid);
		if (existing != null) return idempotent(existing);
		try {
			estoqueService.registrarSaida(positiveInt(body, "produtoId"), positiveInt(body, "localId"),
					quantity(body), session.userId, uuid);
		} catch (SQLException exception) {
			Long duplicate = isDuplicate(exception) ? findMovement(uuid) : null;
			if (duplicate != null) return idempotent(duplicate);
			throw exception;
		}
		return map("message", "Saída registrada com sucesso.");
	}

	private Map<String, Object> registerTransfer(Map<String, Object> body, ApiSession session) throws SQLException {
		String uuid = uuid(body);
		Long existing = findMovement(uuid);
		if (existing != null) return idempotent(existing);
		try {
			estoqueService.transferir(positiveInt(body, "produtoId"), positiveInt(body, "localOrigemId"),
					positiveInt(body, "localDestinoId"), quantity(body), session.userId, uuid);
		} catch (SQLException exception) {
			Long duplicate = isDuplicate(exception) ? findMovement(uuid) : null;
			if (duplicate != null) return idempotent(duplicate);
			throw exception;
		}
		return map("message", "Transferência registrada com sucesso.");
	}

	private Long findMovement(String uuid) throws SQLException {
		try (Connection connection = Conexao.getConexao()) {
			return new MovimentacaoDAO().buscarIdPorUuidCliente(uuid, connection);
		}
	}

	private Map<String, Object> idempotent(Long movementId) {
		return map("id", movementId, "message", "Esta operação já havia sido processada.");
	}

	private static String uuid(Map<String, Object> body) {
		String value = text(body, "uuidCliente");
		try {
			UUID parsed = UUID.fromString(value);
			if (!parsed.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
			return parsed.toString();
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("uuidCliente deve ser um UUID válido.");
		}
	}

	private static String hashPassword(String password) {
		byte[] salt = new byte[16];
		RANDOM.nextBytes(salt);
		PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PASSWORD_ITERATIONS, 256);
		try {
			byte[] derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
			Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
			return "$pbkdf2-sha256$" + PASSWORD_ITERATIONS + "$" + encoder.encodeToString(salt)
					+ "$" + encoder.encodeToString(derived);
		} catch (java.security.GeneralSecurityException exception) {
			throw new IllegalStateException("Não foi possível proteger a senha.", exception);
		} finally {
			spec.clearPassword();
		}
	}

	private static final class ApiSession {
		private final int userId;
		private final String name;
		private final String email;
		private final String profile;
		private final String csrfToken;

		private ApiSession(int userId, String name, String email, String profile, String csrfToken) {
			this.userId = userId;
			this.name = name;
			this.email = email;
			this.profile = profile;
			this.csrfToken = csrfToken;
		}
	}

	private static final class LoginAttempt {
		private static final int MAX_FAILURES = 8;
		private static final long WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(15);
		private int failures;
		private long windowStartedAt;
		private long blockedUntil;

		private synchronized void checkAllowed() {
			long now = System.currentTimeMillis();
			if (blockedUntil > now) {
				throw new HttpFailure(429, "Muitas tentativas de login. Aguarde alguns minutos.");
			}
			if (now - windowStartedAt > WINDOW_MILLIS) {
				failures = 0;
				windowStartedAt = now;
			}
		}

		private synchronized void recordFailure() {
			long now = System.currentTimeMillis();
			if (now - windowStartedAt > WINDOW_MILLIS) {
				failures = 0;
				windowStartedAt = now;
			}
			failures++;
			if (failures >= MAX_FAILURES) {
				blockedUntil = now + WINDOW_MILLIS;
				failures = 0;
			}
		}

		private synchronized void reset() {
			failures = 0;
			blockedUntil = 0;
			windowStartedAt = System.currentTimeMillis();
		}
	}

	private static String profile(Map<String, Object> body) {
		String value = text(body, "perfil").toUpperCase(Locale.ROOT);
		try {
			return Perfil.valueOf(value).name();
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Perfil inválido. Use ADMIN, GESTOR ou OPERADOR.");
		}
	}

	private static String email(Map<String, Object> body) {
		String value = text(body, "email").toLowerCase(Locale.ROOT);
		if (!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
			throw new IllegalArgumentException("Informe um email válido.");
		}
		return value;
	}

	private static LocalDate date(Map<String, Object> body, String key) {
		String value = optionalText(body, key);
		if (value.isEmpty()) return null;
		try {
			return LocalDate.parse(value);
		} catch (DateTimeParseException exception) {
			throw new IllegalArgumentException(key + " deve usar o formato AAAA-MM-DD.");
		}
	}

	private static BigDecimal quantity(Map<String, Object> body) {
		BigDecimal value = decimal(body, "quantidade", false);
		if (value.signum() <= 0 || value.scale() > 3) {
			throw new IllegalArgumentException("A quantidade deve ser positiva e ter no máximo três casas decimais.");
		}
		return value;
	}

	private static int positiveInt(Map<String, Object> body, String key) {
		Object value = body.get(key);
		if (!(value instanceof BigDecimal)) {
			throw new IllegalArgumentException(key + " deve ser um número inteiro positivo.");
		}
		try {
			int result = ((BigDecimal) value).intValueExact();
			if (result <= 0) throw new ArithmeticException();
			return result;
		} catch (ArithmeticException exception) {
			throw new IllegalArgumentException(key + " deve ser um número inteiro positivo.");
		}
	}

	private static int parseId(String value) {
		try {
			int id = Integer.parseInt(value);
			if (id <= 0) throw new NumberFormatException();
			return id;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Identificador inválido.");
		}
	}

	private static int parseLimit(String value) {
		if (value == null || value.isBlank()) return 100;
		try {
			int limit = Integer.parseInt(value);
			if (limit < 1 || limit > 500) throw new NumberFormatException();
			return limit;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("O limite deve estar entre 1 e 500.");
		}
	}

	private static String text(Map<String, Object> body, String key) {
		Object value = body.get(key);
		if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
			throw new IllegalArgumentException(key + " é obrigatório.");
		}
		return ((String) value).trim();
	}

	private static String secret(Map<String, Object> body, String key) {
		Object value = body.get(key);
		if (!(value instanceof String) || ((String) value).isEmpty()) {
			throw new IllegalArgumentException(key + " é obrigatório.");
		}
		if (((String) value).length() > 128) {
			throw new IllegalArgumentException(key + " deve ter no máximo 128 caracteres.");
		}
		return (String) value;
	}

	private static String optionalSecret(Map<String, Object> body, String key) {
		Object value = body.get(key);
		if (value == null || "".equals(value)) return "";
		if (!(value instanceof String) || ((String) value).length() > 128) {
			throw new IllegalArgumentException(key + " deve ter no máximo 128 caracteres.");
		}
		return (String) value;
	}

	private static String optionalText(Map<String, Object> body, String key) {
		Object value = body.get(key);
		if (value == null) return "";
		if (!(value instanceof String)) {
			throw new IllegalArgumentException(key + " deve ser um texto.");
		}
		return ((String) value).trim();
	}

	private static BigDecimal decimal(Map<String, Object> body, String key, boolean allowZero) {
		Object value = body.get(key);
		if (!(value instanceof BigDecimal)) {
			throw new IllegalArgumentException(key + " deve ser numérico.");
		}
		BigDecimal result = (BigDecimal) value;
		if ((allowZero ? result.signum() < 0 : result.signum() <= 0) || result.scale() > 3) {
			throw new IllegalArgumentException(key + " deve ser válido e ter no máximo três casas decimais.");
		}
		return result;
	}

	private static boolean bool(Map<String, Object> body, String key, boolean defaultValue) {
		Object value = body.get(key);
		if (value == null) return defaultValue;
		if (!(value instanceof Boolean)) {
			throw new IllegalArgumentException(key + " deve ser verdadeiro ou falso.");
		}
		return (Boolean) value;
	}

	private static Map<String, Object> readObject(HttpExchange exchange) throws IOException {
		String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
		if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
			throw new HttpFailure(415, "Envie o corpo usando application/json.");
		}
		byte[] bytes = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
		if (bytes.length > MAX_REQUEST_BYTES) {
			throw new HttpFailure(413, "O corpo da requisição excede o limite de 1 MB.");
		}
		Object value = Json.parse(new String(bytes, StandardCharsets.UTF_8));
		if (!(value instanceof Map<?, ?>)) {
			throw new IllegalArgumentException("O corpo JSON deve ser um objeto.");
		}
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
			if (!(entry.getKey() instanceof String)) {
				throw new IllegalArgumentException("Chave inválida no corpo JSON.");
			}
			result.put((String) entry.getKey(), entry.getValue());
		}
		return result;
	}

	private static Map<String, String> parseQuery(String query) {
		Map<String, String> result = new LinkedHashMap<>();
		if (query == null || query.isBlank()) return result;
		for (String pair : query.split("&")) {
			String[] parts = pair.split("=", 2);
			result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
					parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
		}
		return result;
	}

	private static List<Map<String, Object>> query(String sql, RowMapper mapper, Object... parameters)
			throws SQLException {
		try (Connection connection = Conexao.getConexao();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			bind(statement, parameters);
			try (ResultSet resultSet = statement.executeQuery()) {
				List<Map<String, Object>> result = new ArrayList<>();
				while (resultSet.next()) result.add(mapper.map(resultSet));
				return result;
			}
		}
	}

	private static Map<String, Object> insert(String resource, String sql, StatementBinder binder)
			throws SQLException {
		try (Connection connection = Conexao.getConexao();
				PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			binder.bind(statement);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (keys.next()) return map("id", keys.getLong(1), "message", resource + " cadastrado com sucesso.");
			}
		}
		throw new SQLException("O banco não retornou o identificador do novo registro.");
	}

	private static Map<String, Object> update(String table, String resource, int id, String sql,
			StatementBinder binder) throws SQLException {
		try (Connection connection = Conexao.getConexao();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			binder.bind(statement);
			if (statement.executeUpdate() == 0) {
				try (PreparedStatement exists = connection.prepareStatement("SELECT 1 FROM " + table + " WHERE id = ?")) {
					exists.setInt(1, id);
					try (ResultSet rs = exists.executeQuery()) {
						if (!rs.next()) {
							throw new HttpFailure(404, resource + " não encontrado.");
						}
					}
				}
			}
			return map("id", id, "message", resource + " atualizado com sucesso.");
		}
	}

	private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
		for (int index = 0; index < parameters.length; index++) {
			statement.setObject(index + 1, parameters[index]);
		}
	}

	private static Map<String, Object> map(Object... values) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (int index = 0; index < values.length; index += 2) {
			result.put((String) values[index], values[index + 1]);
		}
		return result;
	}

	private static boolean isDuplicate(SQLException exception) {
		for (SQLException current = exception; current != null; current = current.getNextException()) {
			if ("23505".equals(current.getSQLState()) || current.getErrorCode() == 1062) return true;
		}
		return false;
	}

	private static String safeMessage(IllegalArgumentException exception) {
		return exception.getMessage() == null ? "Dados inválidos." : exception.getMessage();
	}

	private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
		send(exchange, status, map("message", message));
	}

	private static void send(HttpExchange exchange, int status, Object body) throws IOException {
		if (status == 204) {
			exchange.sendResponseHeaders(status, -1);
			return;
		}
		byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length);
		exchange.getResponseBody().write(bytes);
	}

	@FunctionalInterface
	private interface RowMapper {
		Map<String, Object> map(ResultSet resultSet) throws SQLException;
	}

	@FunctionalInterface
	private interface StatementBinder {
		void bind(PreparedStatement statement) throws SQLException;
	}

	private static final class HttpFailure extends RuntimeException {
		private static final long serialVersionUID = 1L;
		private final int status;

		private HttpFailure(int status, String message) {
			super(message);
			this.status = status;
		}
	}
}
