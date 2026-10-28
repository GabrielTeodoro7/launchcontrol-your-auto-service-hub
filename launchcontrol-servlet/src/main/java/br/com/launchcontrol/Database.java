package br.com.launchcontrol;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
