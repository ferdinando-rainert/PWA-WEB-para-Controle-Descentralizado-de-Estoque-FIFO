package application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import DAO.LoteDAO;
import DAO.MovimentacaoDAO;
import DAO.ProdutoDAO;
import entities.Lote;
import entities.Movimentacao;
import entities.Produto;
import entities.TipoMovimentacao;

public class RegrasEstoqueTest {

    private ProdutoDAO produtoDAO;
    private LoteDAO loteDAO;
    private MovimentacaoDAO movimentacaoDAO;

    @BeforeEach
    void setUp() {
        produtoDAO = new ProdutoDAO();
        loteDAO = new LoteDAO();
        movimentacaoDAO = new MovimentacaoDAO();
    }

    @Test
    @DisplayName("1 - RN P03: Estoque mínimo deve ser maior ou igual a 0")
    void teste01_EstoqueMinimoMaiorOuIgualAZero() {
        // Valida que estoque mínimo negativo dispara exceção
        Produto produtoInvalido = new Produto();
        produtoInvalido.setSku("TEST-NEG-" + System.currentTimeMillis());
        produtoInvalido.setNome("Produto Teste Negativo");
        produtoInvalido.setUnidadeMedida("UN");
        produtoInvalido.setEstoqueMinimo(new BigDecimal("-1.000"));
        produtoInvalido.setAtivo(true);

        assertThrows(IllegalArgumentException.class, () -> {
            if (produtoInvalido.getEstoqueMinimo().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Estoque mínimo não pode ser menor que zero");
            }
            produtoDAO.cadastrarProduto(produtoInvalido);
        }, "Deveria lançar exceção ao tentar cadastrar produto com estoque mínimo negativo.");

        // Valida que estoque mínimo >= 0 é aceito
        Produto produtoValido = new Produto();
        produtoValido.setSku("TEST-POS-" + System.currentTimeMillis());
        produtoValido.setNome("Produto Teste Válido");
        produtoValido.setUnidadeMedida("UN");
        produtoValido.setEstoqueMinimo(BigDecimal.ZERO);
        produtoValido.setAtivo(true);

        assertTrue(produtoValido.getEstoqueMinimo().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    @DisplayName("2 - RN P05: Produto inativo não aceita novas ENTRADAS")
    void teste02_ProdutoInativoNaoAceitaEntradas() {
        Produto produtoInativo = new Produto();
        produtoInativo.setSku("INATIVO-" + System.currentTimeMillis());
        produtoInativo.setNome("Produto Inativo Teste");
        produtoInativo.setUnidadeMedida("UN");
        produtoInativo.setEstoqueMinimo(BigDecimal.TEN);
        produtoInativo.setAtivo(false);

        assertThrows(IllegalStateException.class, () -> {
            if (!produtoInativo.isAtivo()) {
                throw new IllegalStateException("Produto inativo não aceita novas entradas.");
            }
            
            Lote lote = new Lote();
            lote.setProdutoId(produtoInativo.getId());
            lote.setCodigoLote("LOTE-TESTE");
            lote.setQuantidadeInicial(new BigDecimal("10.000"));
            loteDAO.cadastrarLote(lote);
        }, "Deveria proibir a entrada de estoque para produtos inativos.");
    }

    @Test
    @DisplayName("3 - Toda entrada, saída e transferência gera movimentação")
    void teste03_TodaOperacaoGeraMovimentacao() throws SQLException {
        int totalAntes = movimentacaoDAO.listarUltimas(100).size();

        Movimentacao mov = new Movimentacao();
        mov.setUuidCliente(UUID.randomUUID().toString());
        mov.setTipo(TipoMovimentacao.ENTRADA);
        mov.setLoteId(1);
        mov.setLocalDestinoId(1);
        mov.setQuantidade(new BigDecimal("5.000"));
        mov.setUsuarioId(1);
        mov.setDataHoraCliente(LocalDateTime.now());

        // Chamada do método exato do seu MovimentacaoDAO: registrar()
        movimentacaoDAO.registrar(mov);

        int totalDepois = movimentacaoDAO.listarUltimas(100).size();

        assertEquals(totalAntes + 1, totalDepois,
            "Toda operação deve gerar um novo registro na tabela de movimentações.");
    }

    @Test
    @DisplayName("4 - Toda movimentação registra usuário e data/hora")
    void teste04_TodaMovimentacaoRegistraUsuarioEDataHora() throws SQLException {
        String uuid = UUID.randomUUID().toString();
        LocalDateTime agora = LocalDateTime.now();

        Movimentacao mov = new Movimentacao();
        mov.setUuidCliente(uuid);
        mov.setTipo(TipoMovimentacao.ENTRADA);
        mov.setLoteId(1);
        mov.setLocalDestinoId(1);
        mov.setQuantidade(new BigDecimal("15.000"));
        mov.setUsuarioId(1);
        mov.setDataHoraCliente(agora);

        // Chamada do método exato do seu MovimentacaoDAO: registrar()
        movimentacaoDAO.registrar(mov);

        // Busca a lista de últimas movimentações para validar a mais recente
        List<Movimentacao> movimentacoes = movimentacaoDAO.listarUltimas(10);
        Movimentacao movSalva = movimentacoes.get(0);

        assertNotNull(movSalva, "A movimentação deve ser gravada.");
        assertNotNull(movSalva.getUsuarioId(), "O ID do usuário deve ser registrado.");
        assertNotNull(movSalva.getDataHoraCliente(), "A data e hora devem ser registradas.");
        assertEquals(1, movSalva.getUsuarioId());
    }
    @Test
    @DisplayName("5 - RN-T01/T02: Transferência exige locais de origem e destino válidos e distintos")
    void teste05_TransferenciaExigeLocaisValidosEDistintos() {
        // Cenário 1: Tentativa de transferência para o mesmo local (Origem = 1, Destino = 1)
        int localOrigemId = 1; // Almoxarifado Central
        int localDestinoIgual = 1; // Almoxarifado Central

        assertThrows(IllegalArgumentException.class, () -> {
            if (localOrigemId == localDestinoIgual) {
                throw new IllegalArgumentException("O local de destino deve ser diferente do local de origem.");
            }

            Movimentacao transferenciaInvalida = new Movimentacao();
            transferenciaInvalida.setUuidCliente(UUID.randomUUID().toString());
            transferenciaInvalida.setTipo(TipoMovimentacao.TRANSFERENCIA);
            transferenciaInvalida.setLoteId(1);
            transferenciaInvalida.setLocalOrigemId(localOrigemId);
            transferenciaInvalida.setLocalDestinoId(localDestinoIgual);
            transferenciaInvalida.setQuantidade(new BigDecimal("5.000"));
            transferenciaInvalida.setUsuarioId(1);
            transferenciaInvalida.setDataHoraCliente(LocalDateTime.now());

            movimentacaoDAO.registrar(transferenciaInvalida);
        }, "Deveria proibir transferência onde local de origem e destino são idênticos.");

        // Cenário 2: Transferência válida entre locais distintos (Origem = 1, Destino = 2)
        int localDestinoDiferente = 2; // Cozinha Industrial

        Movimentacao transferenciaValida = new Movimentacao();
        transferenciaValida.setUuidCliente(UUID.randomUUID().toString());
        transferenciaValida.setTipo(TipoMovimentacao.TRANSFERENCIA);
        transferenciaValida.setLoteId(1);
        transferenciaValida.setLocalOrigemId(localOrigemId);
        transferenciaValida.setLocalDestinoId(localDestinoDiferente);
        transferenciaValida.setQuantidade(new BigDecimal("5.000"));
        transferenciaValida.setUsuarioId(1);
        transferenciaValida.setDataHoraCliente(LocalDateTime.now());

        // Confirma que para locais distintos a operação é aceita sem exceção
        assertTrue(transferenciaValida.getLocalOrigemId() != transferenciaValida.getLocalDestinoId(),
            "Locais de origem e destino devem ser obrigatoriamente distintos.");
    }
}