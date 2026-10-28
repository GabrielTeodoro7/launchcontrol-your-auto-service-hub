# LaunchControl

Sistema de serviços automotivos desenvolvido com Java, Jakarta Servlet, MySQL, HTML, CSS e JavaScript.

## Tecnologias

- Java 25 LTS
- Jakarta Servlet 6.1
- MySQL 8+
- JDBC
- HTML5, CSS3 e JavaScript puro
- Maven 3.10+
- Apache Tomcat 11

## Executar no VS Code

### 1. Instalar os programas

Instale o JDK 25, Apache Maven, MySQL Server 8 e Apache Tomcat 11. No VS Code, instale o pacote **Extension Pack for Java**.

Confirme no terminal:

```bash
java -version
mvn -version
```

### 2. Baixar o projeto

No terminal do VS Code:

```bash
git clone https://github.com/GabrielTeodoro7/launchcontrol-your-auto-service-hub.git
cd launchcontrol-your-auto-service-hub
```

Abra essa pasta no VS Code. O código principal está em `launchcontrol-servlet/`.

### 3. Criar o banco de dados

Inicie o MySQL e execute o script:

```sql
source launchcontrol-servlet/database/schema.sql;
```

O comando `source` funciona no cliente de linha de comando do MySQL quando executado a partir da raiz do repositório. Se estiver usando MySQL Workbench, abra o arquivo `launchcontrol-servlet/database/schema.sql` e execute o script nele.

Configure as variáveis de ambiente com os dados do seu banco:

- `LC_DB_URL`: URL JDBC do banco criado pelo script.
- `LC_DB_USER`: usuário do MySQL.
- `LC_DB_PASSWORD`: senha desse usuário.

Não publique senhas no GitHub. Configure essas variáveis no terminal antes de iniciar o servidor.

### 4. Compilar a aplicação

No terminal, execute:

```bash
cd launchcontrol-servlet
mvn clean package
```

O arquivo `target/launchcontrol.war` será criado se a compilação terminar sem erros.

### 5. Executar no Tomcat

Inicie o Tomcat 11 usando o JDK 25 e copie `launchcontrol-servlet/target/launchcontrol.war` para a pasta `webapps` do Tomcat. Inicie ou reinicie o servidor e acesse:

```
http://localhost:8080/launchcontrol/
```

Se o Tomcat estiver configurado em outra porta ou contexto, ajuste o endereço conforme sua instalação.

## Executar pelo GitHub

O GitHub armazena o código e executa verificações automáticas por meio de GitHub Actions. Para conferir o resultado, abra a aba **Actions** deste repositório.

O GitHub não executa diretamente uma aplicação Java Servlet com banco MySQL apenas por abrir o repositório. Para usar o sistema no navegador, compile o WAR e execute-o em um servidor Tomcat com acesso ao banco de dados, ou configure uma hospedagem que ofereça esses serviços.

## Observações

- O esquema do banco está em `launchcontrol-servlet/database/schema.sql`.
- O código Java fica em `launchcontrol-servlet/src/main/java/`.
- As páginas, estilos e scripts ficam em `launchcontrol-servlet/src/main/webapp/`.
- O checkout disponível é demonstrativo e não processa cobranças reais.
- A compilação não substitui o teste completo com Tomcat e MySQL configurados.
