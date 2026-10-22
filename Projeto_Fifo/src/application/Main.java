package application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

import DAO.LocalEstoqueDAO;
import DAO.LoteDAO;
import DAO.MovimentacaoDAO;
import DAO.ProdutoDAO;
import DAO.SaldoLoteLocalDAO;
import DAO.UsuarioDAO;
import entities.LocalEstoque;
import entities.Lote;
import entities.Movimentacao;
import entities.Perfil;
import entities.Produto;
import entities.SaldoLoteLocal;
import entities.Usuario;
import service.EstoqueService;
import ui.MenuEstoque;
import ui.MenuLocal;
import ui.MenuPrincipal;
import ui.MenuProduto;
import ui.MenuUsuario;

public class Main {

	private static final Scanner sc = new Scanner(System.in);
	private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private static final ProdutoDAO produtoDAO = new ProdutoDAO();
	private static final LocalEstoqueDAO localDAO = new LocalEstoqueDAO();
	private static final UsuarioDAO usuarioDAO = new UsuarioDAO();
	private static final LoteDAO loteDAO = new LoteDAO();
	private static final SaldoLoteLocalDAO saldoDAO = new SaldoLoteLocalDAO();
	private static final MovimentacaoDAO movimentacaoDAO = new MovimentacaoDAO();
	private static final EstoqueService estoque = new EstoqueService();

	public static void main(String[] args) {

		int opcao = -1;

		while (opcao != 0) {
			MenuPrincipal.menu();
			opcao = lerInt("Escolha: ");

			switch (opcao) {
			case 1:
				menuProduto();
				break;
			case 2:
				menuLocal();
				break;
			case 3:
				menuEstoque();
				break;
			case 4:
				menuUsuario();
				break;
			case 0:
				System.out.println("Saindo do sistema...");
				break;
			default:
				System.out.println("Opção inválida!");
			}
		}
	}

	// ------ MENU PRODUTO ------
	private static void menuProduto() {

		int opcao = -1;

		while (opcao != 0) {
			MenuProduto.menu();
			opcao = lerInt("Escolha: ");

			switch (opcao) {
			case 1:
				String sku = lerTexto("SKU: ");
				String nome = lerTexto("Nome: ");
				String unidade = lerTexto("Unidade de medida (UN, KG, L...): ");
				BigDecimal minimo = lerDecimal("Estoque mínimo: ");
				produtoDAO.cadastrarProduto(new Produto(sku, nome, unidade, minimo));
				break;

			case 2:
				for (Produto p : produtoDAO.listarTodos()) {
					System.out.printf("[%d] %s - %s (%s) | mínimo: %s | %s%n", p.getId(), p.getSku(), p.getNome(),
							p.getUnidadeMedida(), p.getEstoqueMinimo(), p.isAtivo() ? "ativo" : "inativo");
				}
				break;

			case 3:
				Produto p = new Produto();
				p.setId(lerInt("ID do produto a atualizar: "));
				p.setSku(lerTexto("Novo SKU: "));
				p.setNome(lerTexto("Novo nome: "));
				p.setUnidadeMedida(lerTexto("Nova unidade de medida: "));
				p.setEstoqueMinimo(lerDecimal("Novo estoque mínimo: "));
				produtoDAO.atualizarProduto(p);
				break;

			case 4:
				produtoDAO.inativarProduto(lerInt("ID do produto a inativar: "));
				break;

			case 0:
				break;
			default:
				System.out.println("Opção inválida!");
			}
		}
	}

	// ------ MENU LOCAL ------
	private static void menuLocal() {

		int opcao = -1;

		while (opcao != 0) {
			MenuLocal.menu();
			opcao = lerInt("Escolha: ");

			switch (opcao) {
			case 1:
				String nome = lerTexto("Nome do local: ");
				String descricao = lerTexto("Descrição: ");
				localDAO.cadastrarLocal(new LocalEstoque(nome, descricao));
				break;

			case 2:
				for (LocalEstoque l : localDAO.listarTodos()) {
					System.out.printf("[%d] %s - %s | %s%n", l.getId(), l.getNome(), l.getDescricao(),
							l.isAtivo() ? "ativo" : "inativo");
				}
				break;

			case 0:
				break;
			default:
				System.out.println("Opção inválida!");
			}
		}
	}

	// ------ MENU ESTOQUE ------
	private static void menuEstoque() {

		int opcao = -1;

		while (opcao != 0) {
			MenuEstoque.menu();
			opcao = lerInt("Escolha: ");

			try {
				switch (opcao) {
				case 1:
					int produtoEntrada = lerInt("ID do produto: ");
					int localEntrada = lerInt("ID do local de destino: ");
					String codigo = lerTexto("Código do lote: ");
					LocalDate validade = lerData("Validade (aaaa-mm-dd, Enter para pular): ");
					BigDecimal qtdEntrada = lerDecimal("Quantidade: ");
					int usuarioEntrada = lerInt("ID do usuário responsável: ");

					estoque.registrarEntrada(produtoEntrada, localEntrada, codigo, validade, qtdEntrada,
							usuarioEntrada);
					System.out.println("Entrada registrada! Lote criado.");
					break;

				case 2:
					int produtoSaida = lerInt("ID do produto: ");
					int localSaida = lerInt("ID do local de origem: ");
					BigDecimal qtdSaida = lerDecimal("Quantidade: ");
					int usuarioSaida = lerInt("ID do usuário responsável: ");

					List<String> usados = estoque.registrarSaida(produtoSaida, localSaida, qtdSaida, usuarioSaida);
					System.out.println("Saída registrada! Lotes utilizados (FIFO):");
					usados.forEach(u -> System.out.println("  " + u));
					break;

				case 3:
					int produtoTransf = lerInt("ID do produto: ");
					int origem = lerInt("ID do local de origem: ");
					int destino = lerInt("ID do local de destino: ");
					BigDecimal qtdTransf = lerDecimal("Quantidade: ");
					int usuarioTransf = lerInt("ID do usuário responsável: ");

					List<String> movidos = estoque.transferir(produtoTransf, origem, destino, qtdTransf,
							usuarioTransf);
					System.out.println("Transferência registrada! Lotes movidos (FIFO):");
					movidos.forEach(u -> System.out.println("  " + u));
					break;

				case 4:
					int produtoSaldo = lerInt("ID do produto: ");
					int localSaldo = lerInt("ID do local: ");

					System.out.println("Saldo total: " + saldoDAO.saldoTotal(produtoSaldo, localSaldo));
					System.out.println("Lotes em ordem FIFO (o primeiro é o próximo a sair):");
					for (SaldoLoteLocal s : saldoDAO.listarFifo(produtoSaldo, localSaldo)) {
						Lote l = loteDAO.buscarPorId(s.getLoteId());
						System.out.printf("  Lote %s | entrada: %s | quantidade: %s%n", l.getCodigoLote(),
								l.getDataEntrada().format(FORMATO), s.getQuantidade());
					}
					break;

				case 5:
					for (Movimentacao m : movimentacaoDAO.listarUltimas(20)) {
						System.out.printf("#%d %s | %s | lote %d | origem %s -> destino %s | qtd %s | usuário %d%n",
								m.getId(), m.getDataHoraCliente().format(FORMATO), m.getTipo(), m.getLoteId(),
								m.getLocalOrigemId(), m.getLocalDestinoId(), m.getQuantidade(), m.getUsuarioId());
					}
					break;

				case 0:
					break;
				default:
					System.out.println("Opção inválida!");
				}

			} catch (IllegalArgumentException e) {
				System.out.println("Não foi possível concluir: " + e.getMessage());
			} catch (SQLException e) {
				System.out.println("Erro no banco de dados: " + e.getMessage());
			}
		}
	}

	// ------ MENU USUÁRIO ------
	private static void menuUsuario() {

		int opcao = -1;

		while (opcao != 0) {
			MenuUsuario.menu();
			opcao = lerInt("Escolha: ");

			switch (opcao) {
			case 1:
				String nome = lerTexto("Nome: ");
				String email = lerTexto("E-mail: ");
				String senha = lerTexto("Senha: ");
				String perfil = lerTexto("Perfil (ADMIN, GESTOR ou OPERADOR): ").toUpperCase();
				try {
					usuarioDAO.cadastrarUsuario(new Usuario(nome, email, gerarHash(senha), Perfil.valueOf(perfil)));
				} catch (IllegalArgumentException e) {
					System.out.println("Perfil inválido.");
				}
				break;

			case 2:
				for (Usuario u : usuarioDAO.listarTodos()) {
					System.out.printf("[%d] %s <%s> | %s | %s%n", u.getId(), u.getNome(), u.getEmail(), u.getPerfil(),
							u.isAtivo() ? "ativo" : "inativo");
				}
				break;

			case 0:
				break;
			default:
				System.out.println("Opção inválida!");
			}
		}
	}

	// ------ LEITURA SEGURA DO TECLADO ------
	private static String lerTexto(String mensagem) {
		System.out.print(mensagem);
		return sc.nextLine().trim();
	}

	private static int lerInt(String mensagem) {
		while (true) {
			try {
				return Integer.parseInt(lerTexto(mensagem));
			} catch (NumberFormatException e) {
				System.out.println("Digite um número inteiro válido.");
			}
		}
	}

	private static BigDecimal lerDecimal(String mensagem) {
		while (true) {
			try {
				return new BigDecimal(lerTexto(mensagem).replace(',', '.'));
			} catch (NumberFormatException e) {
				System.out.println("Digite um número válido (ex.: 12,5).");
			}
		}
	}

	private static LocalDate lerData(String mensagem) {
		while (true) {
			String texto = lerTexto(mensagem);
			if (texto.isEmpty()) {
				return null;
			}
			try {
				return LocalDate.parse(texto);
			} catch (DateTimeParseException e) {
				System.out.println("Data inválida. Use aaaa-mm-dd.");
			}
		}
	}

	// Hash SHA-256 só para fins de estudo (em produção use BCrypt)
	private static String gerarHash(String senha) {
		try {
			byte[] bytes = MessageDigest.getInstance("SHA-256").digest(senha.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : bytes) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
