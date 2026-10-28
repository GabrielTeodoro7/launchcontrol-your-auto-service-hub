package br.com.launchcontrol;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Centraliza a criação de conexões JDBC com o banco de dados.
 *
 */
public final class Database {
    private Database() {}

    // Obtém as credenciais do ambiente e abre uma conexão JDBC.
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
