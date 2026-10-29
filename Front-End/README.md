# Integração do Estoque FIFO

## Iniciar o banco e a API Java

1. Inicie o MySQL na porta `3305`. Para um banco novo, execute `Projeto_Fifo/db/schema-mysql.sql` no MySQL Workbench. **Não execute esse script sem revisar se já existe um banco com dados**; compare a estrutura e faça uma cópia de segurança antes de migrar uma instalação existente.
2. No PowerShell, compile e inicie a API:

```powershell
Set-Location Projeto_Fifo
$sources = Get-ChildItem src -Recurse -Filter *.java |
    Where-Object { $_.Name -ne 'RegrasEstoqueTest.java' } |
    ForEach-Object { $_.FullName }
javac --release 17 --add-modules jdk.httpserver -encoding UTF-8 -cp 'lib/*' -d bin $sources

$env:FIFO_DB_URL = 'jdbc:mysql://localhost:3305/estoque_fifo?serverTimezone=UTC&useSSL=false'
$env:FIFO_DB_USER = 'root'
# Defina FIFO_DB_PASSWORD se o usuário do banco tiver senha.
$env:FIFO_COOKIE_SECURE = 'false' # Somente para desenvolvimento local em HTTP.
java --add-modules jdk.httpserver -cp 'bin;lib/*' application.ApiServer
```

O servidor escuta somente em `127.0.0.1:8080` por padrão. Confirme a conexão abrindo `http://localhost:8080/api/v1/health`; a resposta deve conter `"status":"ok"` e `"database":"connected"`.

Para usar outras credenciais ou porta, configure `FIFO_DB_URL`, `FIFO_DB_USER`, `FIFO_DB_PASSWORD`, `FIFO_API_PORT` e `FIFO_API_CORS_ORIGINS` no mesmo terminal antes de iniciar o servidor. A origem padrão permitida é `http://127.0.0.1:5500` ou `http://localhost:5500`.

Em uma base nova, crie a primeira conta de administrador uma única vez, em um terminal interativo:

```powershell
java --add-modules jdk.httpserver -cp 'bin;lib/*' application.ApiServer create-admin
```

O comando solicita nome, email e senha sem exibi-la; só funciona enquanto a tabela `usuario` estiver vazia. Para atualizar a senha de uma conta ativa existente:

```powershell
java --add-modules jdk.httpserver -cp 'bin;lib/*' application.ApiServer reset-password admin@exemplo.com
```

Use uma senha com pelo menos 12 caracteres. Esses comandos precisam das mesmas variáveis de banco configuradas no terminal.

## Iniciar o front-end

Sirva a pasta `Front-End` por HTTP (por exemplo, com a extensão Live Server do VS Code) e abra `index.html`. A URL padrão da API é `http://localhost:8080/api/v1`; se necessário, altere-a pelo botão **API** no cabeçalho. A configuração fica salva no navegador. O botão **Atualizar** recarrega os dados do servidor.

As páginas `index.html` e `estoque_fifo_pwa.html` usam o mesmo cliente HTTP. O navegador não se conecta diretamente ao MySQL.

## Rotas disponíveis

As rotas retornam JSON e usam propriedades camelCase. Erros usam `{"message":"descrição"}`.

| Método | Rota | Uso |
|---|---|---|
| GET | `/health` | Confirma que API e banco estão acessíveis |
| POST | `/auth/login` | Inicia sessão com email e senha |
| GET | `/auth/session` | Consulta a sessão autenticada |
| POST | `/auth/logout` | Encerra a sessão autenticada |
| GET, POST | `/produtos` | Consulta e cadastro |
| PUT | `/produtos/{id}` | Atualiza ou inativa |
| GET, POST | `/locais` | Consulta e cadastro |
| PUT | `/locais/{id}` | Atualiza ou inativa |
| GET, POST | `/usuarios` | Consulta sem hash de senha e cadastro |
| PUT | `/usuarios/{id}` | Atualiza; senha é opcional |
| GET | `/lotes` | Consulta lotes |
| GET | `/estoque/saldos` | Consulta saldos por lote/local |
| GET | `/movimentacoes?limite=100` | Consulta histórico (limite de 1 a 500) |
| POST | `/estoque/entradas` | Cria lote e registra entrada |
| POST | `/estoque/saidas` | Registra saída usando FIFO |
| POST | `/estoque/transferencias` | Transfere lotes usando FIFO |

Todas as rotas, exceto `/health` e `/auth/login`, exigem sessão válida. Requisições que alteram dados também exigem o token CSRF enviado no cabeçalho `X-CSRF-Token`. O usuário da sessão é associado às movimentações pela API; não envie `usuarioId` como fonte de identidade.

Exemplos de payload:

```json
{"sku":"ARR-5KG","nome":"Arroz 5kg","unidadeMedida":"UN","estoqueMinimo":10,"ativo":true}
```

```json
{"produtoId":1,"localId":1,"codigoLote":"L004","dataValidade":"2027-03-10","quantidade":12.5,"uuidCliente":"550e8400-e29b-41d4-a716-446655440000"}
```

```json
{"produtoId":1,"localId":1,"quantidade":2.5,"uuidCliente":"550e8400-e29b-41d4-a716-446655440001"}
```

```json
{"produtoId":1,"localOrigemId":1,"localDestinoId":2,"quantidade":2.5,"uuidCliente":"550e8400-e29b-41d4-a716-446655440002"}
```

O FIFO é ordenado por `data_entrada ASC, id ASC`. A chave `uuidCliente` tem índice único no banco e permite que a fila offline reenvie uma operação sem duplicar o estoque. Quantidades aceitam até três casas decimais.

Na base existente, valores negativos de estoque mínimo são mostrados como inválidos; o painel os considera como zero até que sejam corrigidos no cadastro. A API rejeita novos valores negativos.

## Publicar o front-end na Vercel

A Vercel hospeda os arquivos estáticos e a função proxy Node.js; ela **não** hospeda esta API Java nem o MySQL. Hospede a API e o banco em serviços separados. A API precisa estar disponível por HTTPS, e o banco não deve ser exposto à internet.

1. Faça deploy da pasta `Front-End` como raiz do projeto Vercel (framework **Other**, sem comando de build; saída `.`).
2. Em **Project Settings → Environment Variables**, configure:
   - `FIFO_API_ORIGIN`: origem HTTPS pública da API Java, sem caminho, por exemplo `https://api.exemplo.com`.
   - `FIFO_API_PROXY_SECRET`: segredo aleatório de pelo menos 32 caracteres. Gere um segredo exclusivo e defina o mesmo valor na Vercel e no serviço da API. Mantenha-o somente como variável server-side; nunca use prefixo `VITE_`, `NEXT_PUBLIC_` ou equivalente.
3. Configure na API Java:
   - `FIFO_API_PROXY_SECRET`: o mesmo segredo da função Vercel.
   - `FIFO_COOKIE_SECURE=true`.
   - `FIFO_API_CORS_ORIGINS`: lista separada por vírgula contendo cada origem exata do front, por exemplo `https://meu-projeto.vercel.app,https://estoque.exemplo.com`. Inclua os domínios de preview que realmente serão usados.
   - `FIFO_DB_URL`, `FIFO_DB_USER` e `FIFO_DB_PASSWORD`: credenciais de um usuário MySQL restrito ao banco da aplicação.
4. Configure o serviço Java atrás de HTTPS. Se usar um proxy reverso no mesmo host, mantenha a API Java ligada a `127.0.0.1`; se o provedor exigir `0.0.0.0`, mantenha o tráfego externo limitado ao HTTPS do provedor. O segredo do proxy é uma defesa adicional, não substitui HTTPS nem as regras de rede.
5. Execute `Projeto_Fifo/db/schema-mysql.sql` numa base nova. Para uma base existente, faça backup e execute `Projeto_Fifo/db/migration-auth-session.sql`; revise separadamente qualquer outra alteração de esquema antes de aplicá-la. Depois, use `create-admin` (base vazia) ou `reset-password email` (conta existente) no terminal do serviço com acesso ao banco.
6. Reinicie a API e faça novo deploy da Vercel depois de configurar as variáveis. A rota `https://seu-dominio/api/v1/health` passa pelo proxy; ela deve responder com `"status":"ok"` e `"database":"connected"`.

O login cria uma sessão de 12 horas com cookie `HttpOnly`, `Secure` e `SameSite=Lax`, proteção CSRF e bloqueio temporário após tentativas inválidas. Senhas são armazenadas com PBKDF2; a senha e o token de sessão não são persistidos no `localStorage`. O proxy limita métodos e tamanho de requisição, encaminha somente cabeçalhos necessários e mantém o segredo fora do navegador. Os controles de perfil também são aplicados pela API; ocultar elementos da interface não é uma medida de autorização.

Em desenvolvimento, `FIFO_COOKIE_SECURE=false` é permitido apenas para loopback HTTP. Não use essa configuração em um ambiente acessível pela rede. Quando a API fica indisponível, o front pode enfileirar operações de estoque na sessão atual; a fila é removida ao sair ou quando a sessão expira. O navegador não deve ser usado como substituto de backup ou de disponibilidade do banco.
