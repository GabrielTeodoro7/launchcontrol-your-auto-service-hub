package br.com.launchcontrol;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Centraliza a conexão entre o Java e o banco MySQL. Assim, outras classes não precisam repetir a configuração do banco.
 *
 * Uma classe é como uma caixa que reúne dados e ações relacionados a uma tarefa.
 */
public final class Database {
    private Database() {}

    public static Connection getConnection() throws SQLException {
        String url = System.getenv("LC_DB_URL");
        String user = System.getenv("LC_DB_USER");
        String password = System.getenv("LC_DB_PASSWORD");

        if (url == null || user == null || password == null) {
            throw new SQLException("Configure LC_DB_URL, LC_DB_USER e LC_DB_PASSWORD.");
        }
        return DriverManager.getConnection(url, user, password);
    }
}
