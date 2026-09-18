# PWA Web para Controle Descentralizado de Estoque FIFO

Sistema web responsivo desenvolvido para realizar o **controle descentralizado de estoque**, permitindo o gerenciamento de produtos, lotes e movimentações de estoque utilizando o método **FIFO (First In, First Out)**.

O projeto tem como objetivo aplicar conceitos de desenvolvimento de sistemas, arquitetura de software, banco de dados e regras de negócio em uma aplicação voltada para gerenciamento de estoque.

---

## 📌 Sobre o Projeto

O sistema foi desenvolvido com a proposta de centralizar e organizar o controle de estoque de diferentes setores ou unidades, permitindo o acompanhamento das entradas e saídas de produtos.

A principal regra de negócio do sistema é a utilização do método **FIFO**, no qual os produtos que entraram primeiro no estoque devem ser priorizados nas saídas.

### Exemplo

Considerando um produto com os seguintes lotes:

| Lote | Data de Entrada | Quantidade |
|------|------------------|------------|
| L001 | 01/09/2026 | 10 |
| L002 | 05/09/2026 | 20 |
| L003 | 10/09/2026 | 15 |

Caso seja realizada uma saída de **15 unidades**, o sistema deverá utilizar:

```text
L001 → 10 unidades
L002 → 5 unidades

Dessa forma, o lote mais antigo é consumido primeiro.

🎯 Objetivos
Controlar produtos e seus respectivos estoques;
Gerenciar lotes de produtos;
Registrar entradas e saídas;
Aplicar a regra FIFO nas movimentações;
Permitir o controle descentralizado de diferentes estoques;
Manter histórico das movimentações;
Facilitar a consulta da quantidade disponível em estoque;
Reduzir inconsistências no controle manual de estoque;
Aplicar conceitos de desenvolvimento de software em um sistema realista.
🚀 Funcionalidades
Produtos
 Cadastro de produtos
 Alteração de produtos
 Consulta de produtos
 Inativação de produtos
Estoque
 Consulta de estoque
 Entrada de produtos
 Saída de produtos
 Controle por unidade/setor
 Consulta de saldo disponível
Lotes
 Cadastro de lotes
 Controle de quantidade por lote
 Registro da data de entrada
 Controle de validade
 Aplicação da regra FIFO
Movimentações
 Registro de entradas
 Registro de saídas
 Histórico de movimentações
 Identificação do lote utilizado
 Identificação do usuário responsável
Usuários
 Cadastro de usuários
 Autenticação
 Controle de acesso
 Permissões por perfil
PWA
 Interface responsiva
 Instalação como aplicativo
 Funcionamento em dispositivos móveis
 Estratégia de funcionamento offline
 Sincronização dos dados
🏗️ Arquitetura

A aplicação será estruturada utilizando uma arquitetura baseada em API REST.

┌──────────────────────────────┐
│          Frontend            │
│       React + TypeScript     │
│                              │
│             PWA              │
└──────────────┬───────────────┘
               │
               │ HTTP / JSON
               ▼
┌──────────────────────────────┐
│          Backend             │
│       Java + Spring Boot     │
│                              │
│ Controllers                  │
│ Services                     │
│ Repositories                 │
│ DTOs                         │
│ Security                     │
└──────────────┬───────────────┘
               │
               │ JPA / Hibernate
               ▼
┌──────────────────────────────┐
│           MySQL              │
│                              │
│ Produtos                     │
│ Estoques                     │
│ Lotes                        │
│ Movimentações                │
│ Usuários                     │
└──────────────────────────────┘
🛠️ Tecnologias
Backend
Java
Spring Boot
Spring Data JPA
Hibernate
Spring Security
JWT
Maven
JUnit
Mockito
Frontend
React
TypeScript
Vite
HTML5
CSS3
PWA
Banco de Dados
MySQL
Ferramentas
Git
GitHub
Docker
Postman
📂 Estrutura do Projeto

A estrutura planejada para o projeto:

PWA-WEB-para-Controle-Descentralizado-de-Estoque-FIFO/
│
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/
│   │   │   │       └── estoque/
│   │   │   │           ├── controller/
│   │   │   │           ├── service/
│   │   │   │           ├── repository/
│   │   │   │           ├── entity/
│   │   │   │           ├── dto/
│   │   │   │           ├── exception/
│   │   │   │           ├── security/
│   │   │   │           └── config/
│   │   │   └── resources/
│   │   │       └── application.properties
│   │   │
│   │   └── test/
│   │
│   └── pom.xml
│
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── services/
│   │   ├── hooks/
│   │   ├── contexts/
│   │   ├── types/
│   │   └── utils/
│   │
│   ├── public/
│   └── package.json
│
├── database/
│   └── scripts/
│
├── docker/
│
└── README.md
🗄️ Modelo de Dados

Entre as principais entidades previstas estão:

USUÁRIO
   │
   │
   ▼
ESTOQUE ─────────── PRODUTO
                      │
                      │
                      ▼
                    LOTE
                      │
                      │
                      ▼
              MOVIMENTAÇÃO
Principais entidades

Produto

Responsável pelas informações básicas dos produtos armazenados.

Estoque

Representa a quantidade disponível de determinado produto em uma unidade ou setor.

Lote

Representa uma entrada específica de determinado produto, permitindo controlar quantidade, data de entrada e validade.

Movimentação

Registra todas as operações de entrada e saída realizadas no estoque.

Usuário

Responsável por identificar quem realizou determinada operação no sistema.

🔄 Regra FIFO

O método FIFO (First In, First Out) determina que os produtos mais antigos sejam utilizados antes dos produtos mais recentes.

A aplicação deverá identificar automaticamente os lotes disponíveis e realizar a baixa começando pelo lote com a entrada mais antiga.

Entrada

Lote A → 10 unidades
Lote B → 20 unidades
Lote C → 15 unidades

        ↓

Saída de 25 unidades

        ↓

Lote A → 10 unidades
Lote B → 15 unidades

        ↓

Saldo

Lote A → 0
Lote B → 5
Lote C → 15

A implementação dessa regra ficará concentrada no backend, garantindo que as regras de negócio não dependam do frontend.

🔐 Segurança

O sistema deverá possuir autenticação e autorização para controlar o acesso às funcionalidades.

A implementação prevista utiliza:

Spring Security;
JWT;
Controle de usuários;
Perfis de acesso;
Autorização por funcionalidade.
📱 Progressive Web App

Por ser uma PWA, o sistema será desenvolvido pensando também em dispositivos móveis.

Entre as características planejadas:

Interface responsiva;
Instalação no dispositivo;
Acesso por navegador;
Service Worker;
Cache de recursos;
Possibilidade de funcionamento offline;
Sincronização posterior dos dados.
🧪 Testes

O backend será desenvolvido utilizando testes automatizados para validar principalmente as regras de negócio.

Exemplos:

✔ Cadastro de produto
✔ Entrada de estoque
✔ Saída de estoque
✔ Saída utilizando FIFO
✔ Saída envolvendo múltiplos lotes
✔ Tentativa de saída maior que o estoque disponível
✔ Controle de permissões
📋 Regras de Negócio

Algumas regras previstas:

Um produto pode possuir vários lotes.
Cada lote possui sua própria quantidade disponível.
Toda entrada deve gerar uma movimentação.
Toda saída deve gerar uma movimentação.
As saídas devem respeitar a regra FIFO.
Um lote não pode possuir quantidade negativa.
Não deve ser permitida uma saída superior ao estoque disponível.
As movimentações devem possuir registro de data e usuário responsável.
Produtos inativos não devem permitir novas movimentações de entrada.
O histórico de movimentações não deve ser apagado durante operações comuns do sistema.
📈 Evolução do Projeto

O desenvolvimento será realizado de forma incremental:

[1] Planejamento
       ↓
[2] Modelagem do Banco
       ↓
[3] Desenvolvimento da API
       ↓
[4] Implementação do FIFO
       ↓
[5] Desenvolvimento do Frontend
       ↓
[6] Autenticação
       ↓
[7] Implementação PWA
       ↓
[8] Testes
       ↓
[9] Dockerização
       ↓
[10] Deploy
📌 Status

🚧 Em desenvolvimento

O projeto encontra-se em fase inicial de desenvolvimento.

As funcionalidades serão implementadas gradualmente conforme a evolução do projeto.

👨‍💻 Autor

Ferdinando Rainert

Projeto desenvolvido para fins de estudo, prática de desenvolvimento de software e construção de portfólio.

📄 Licença

Este projeto está disponível para fins educacionais e de estudo.


**Eu manteria exatamente essa linha por enquanto:** o README apresenta a arquitetura e o que será desenvolvido, mas marca as funcionalidades como `Em desenvolvimento`. Isso evita que o GitHub pareça dizer que o sistema já possui autenticação, FIFO, PWA etc. quando ainda não estão implementados.

E, para o seu caso, eu começaria o repositório pelo **backend Java + Spring Boot + MySQL**, implementando primeiro **Produto → Lote → Movimentação → regra FIFO**. Depois entraria com o React/PWA.
