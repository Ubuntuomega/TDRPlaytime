package nl.thedutchruben.playtime.database;

import nl.thedutchruben.mccore.utils.config.FileManager;
import nl.thedutchruben.playtime.Playtime;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.logging.Level;

public class MysqlConnectionPool
{

    private LinkedList<Connection> availableConnections;
    private HashSet<Connection> usedConnections;

    private String dbUrl;
    private String dbUser;
    private String dbPasswd;

    public MysqlConnectionPool(String dbUrl, String user, String password){
        availableConnections = new LinkedList<>();
        usedConnections = new HashSet<>();

        this.dbUrl = dbUrl;
        this.dbUser = user;
        this.dbPasswd = password;
    }


    public synchronized Connection getConnection(){
        Connection connection = null;


        if (availableConnections.size() > 0){
            connection = availableConnections.poll();

            try {
                if (connection.isValid(0)) {
                    usedConnections.add(connection);
                } else {
                    connection = createConnection();
                    usedConnections.add(connection);
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }


        } else {
            connection = createConnection();
            usedConnections.add(connection);
        }

        return connection;

    }

    public synchronized void close(Connection connection){
        if (usedConnections.contains(connection)){
            usedConnections.remove(connection);
            availableConnections.add(connection);
        }
    }

    public void stop() throws SQLException {

        for (Connection connection : availableConnections){
            connection.close();
        }

        for (Connection connection : usedConnections) {
            connection.close();
        }

    }

    private Connection createConnection(){
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(
                    dbUrl, dbUser, dbPasswd);

        }catch (Exception exception){
            Bukkit.getLogger().log(Level.WARNING,"Sql not connected plugin shutting down");
            Playtime.getInstance().getServer().getPluginManager().disablePlugin(Playtime.getInstance());
        }

        return connection;
    }
}
