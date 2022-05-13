package nl.thedutchruben.playtime.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.SneakyThrows;
import nl.thedutchruben.mccore.utils.config.FileManager;
import nl.thedutchruben.playtime.Playtime;
import nl.thedutchruben.playtime.milestone.Milestone;
import nl.thedutchruben.playtime.milestone.RepeatingMilestone;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class MysqlDatabase extends Storage {
    //private Connection connection;
    private final FileManager.Config config = Playtime.getInstance().getFileManager().getConfig("database.yml");
    private Gson gson;

    private MysqlConnectionPool connectionPool;


    private String tablePrefix = "";

    @Override
    public String getName() {
        return "mysql";
    }

    @Override
    public boolean setup() {

        this.connectionPool = new MysqlConnectionPool("jdbc:mysql://" + config.get().getString("mysql.hostname") + ":" + config.get().getInt("mysql.port") + "/" + config.get().getString("mysql.database"),config.get().getString("mysql.user"),  config.get().getString("mysql.password"));

        Connection connection = connectionPool.getConnection();

        this.gson = new GsonBuilder()
                .disableHtmlEscaping().setPrettyPrinting().create();

        tablePrefix = config.get().getString("mysql.table_prefix");

        String ex = "CREATE TABLE IF NOT EXISTS `" + tablePrefix + "playtime` (\n" +
                "  `uuid` varchar(36),\n" +
                "  `name` varchar(16),\n" +
                "  `time` BIGINT, \n" +
                "   PRIMARY KEY (uuid) \n"+
                ");\n";

        try (PreparedStatement preparedStatement = connection.prepareStatement(ex)) {
            preparedStatement.execute();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return false;
        }

        String update = "ALTER TABLE " + tablePrefix + "playtime MODIFY uuid VARCHAR(36);";

        try (PreparedStatement preparedStatement = connection.prepareStatement(update)) {
            preparedStatement.execute();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return false;
        }

        String miletones = "CREATE TABLE IF NOT EXISTS `" + tablePrefix + "milestones` (\n" +
                "  `name` varchar(40),\n" +
                "  `data` TEXT \n" +
                ");\n";

        try (PreparedStatement preparedStatement = connection.prepareStatement(miletones)) {
            preparedStatement.execute();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return false;
        }

        String repmiletones = "CREATE TABLE IF NOT EXISTS `" + tablePrefix + "repeating_milestones` (\n" +
                "  `name` varchar(40),\n" +
                "  `data` TEXT \n" +
                ");\n";

        try (PreparedStatement preparedStatement = connection.prepareStatement(repmiletones)) {
            preparedStatement.execute();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public void stop() {
        try {
            connectionPool.stop();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    @SneakyThrows
    @Override
    public CompletableFuture<Long> getPlayTimeByUUID(String uuid) {

        return CompletableFuture.supplyAsync(() -> {
            Connection connection = connectionPool.getConnection();
            long time = 0L;

            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT `time` FROM `" + tablePrefix + "playtime` WHERE `uuid` = ?")){

                preparedStatement.setString(1, uuid);

                ResultSet resultSet = preparedStatement.executeQuery();

                if (resultSet.next()) {
                    time = resultSet.getLong("time");
                    resultSet.close();
                } else {
                    createPlayerTimeProfile(uuid);
                }

                resultSet.close();

            } catch(SQLException sqlException){
                sqlException.printStackTrace();
            } finally {

                connectionPool.close(connection);
            }

            return time;
        });
    }

    private void createPlayerTimeProfile(String uuid){

        Connection connection = connectionPool.getConnection();

        try (PreparedStatement preparedStatement =  connection.prepareStatement("INSERT INTO `" + tablePrefix + "playtime`(`uuid`, `name`, `time`) VALUES (?,?,?)")){

            Player player = Bukkit.getPlayer(UUID.fromString(uuid));
            String playerName = "";

            if (player != null) {
                playerName = player.getName();
            }

            preparedStatement.setString(1, uuid);
            preparedStatement.setString(2, playerName);
            preparedStatement.setLong(3, 0L);

            preparedStatement.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            connectionPool.close(connection);
        }

    }


    @SneakyThrows
    @Override
    public CompletableFuture<Long> getPlayTimeByName(String name) {

        return CompletableFuture.supplyAsync(() -> {

            long time = 0;
            Connection connection = connectionPool.getConnection();

            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT `time` FROM `" + tablePrefix + "playtime` WHERE `name` = ?")){
                preparedStatement.setString(1, name);

                ResultSet resultSet = preparedStatement.executeQuery();

                if (resultSet.next()){
                    time = resultSet.getLong("time");
                }

                resultSet.close();
            } catch (SQLException exception){
                exception.printStackTrace();
            } finally {
                connectionPool.close(connection);
            }

            return time;

        });
    }

    @SneakyThrows
    @Override
    public CompletableFuture savePlayTime(String uuid, long playtime) {

        return CompletableFuture.supplyAsync(() -> {
            Connection connection = connectionPool.getConnection();

            try (PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO`" + tablePrefix + "playtime`(uuid, name, time) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE `time` = ?")){
                String name = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();

                preparedStatement.setString(1, uuid);
                preparedStatement.setString(2, name);
                preparedStatement.setLong(3, playtime);
                preparedStatement.setLong(4, playtime);

                preparedStatement.execute();
            } catch (SQLException sqlException) {
                sqlException.printStackTrace();
            } finally {
                connectionPool.close(connection);
            }


            return this;
        });
    }

    //Used for onDisable() and migration
    @Override
    public void savePlayTimeSync(String uuid, long playtime) {
        Connection connection = connectionPool.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO`" + tablePrefix + "playtime`(uuid, name, time) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE `time` = ?")){
            String name = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();

            preparedStatement.setString(1, uuid);
            preparedStatement.setString(2, name);
            preparedStatement.setLong(3, playtime);
            preparedStatement.setLong(4, playtime);

            preparedStatement.execute();

        } catch (SQLException sqlException) {
            sqlException.printStackTrace();
        } finally {
            connectionPool.close(connection);
        }
    }

    @SneakyThrows
    @Override
    public CompletableFuture<Map<String, Long>> getTopTenList() {

        return CompletableFuture.supplyAsync(() -> {
            Map<String, Long> topList = new LinkedHashMap<>();
            Connection connection = connectionPool.getConnection();

            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM `" + tablePrefix + "playtime`ORDER BY `time` DESC  LIMIT 10 ")) {
                ResultSet resultSet = preparedStatement.executeQuery();

                while (resultSet.next()) {
                    topList.put(resultSet.getString("name"), resultSet.getLong("time"));
                }

                resultSet.close();

            } catch (SQLException sqlException) {
                sqlException.printStackTrace();
            } finally {
                connectionPool.close(connection);
            }

            return topList;
        });
    }

    @Override
    public long getTotalPlayTime() {

        Connection connection = connectionPool.getConnection();
        long time = 0;

        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT SUM(`time`) AS TotalTime FROM `" + tablePrefix + "playtime`")) {
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                time =  resultSet.getLong("TotalTime");
            }

            resultSet.close();

        }catch (SQLException sqlException) {
            sqlException.printStackTrace();
        } finally {
            connectionPool.close(connection);
        }

        return time;
    }

    @Override
    public int getTotalPlayers() {
        Connection connection = connectionPool.getConnection();
        int totalPlayers = 0;

        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT COUNT(*) AS totalPlayers from `" + tablePrefix + "playtime`")){
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()){
                totalPlayers = resultSet.getInt("totalPlayers");
            }

            resultSet.close();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            connectionPool.close(connection);
        }

        return totalPlayers;
    }

    @Override
    public String getTopPlace(int place) {

        Connection connection = connectionPool.getConnection();
        String playerName = "";

        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT `name` FROM " + tablePrefix + "`playtime`ORDER BY `time` DESC LIMIT "+(place + 1)+","+(place + 1)+"")){
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()){
                playerName = resultSet.getString("name");
            }

            resultSet.close();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            connectionPool.close(connection);
        }

        return playerName;

    }

    @Override
    public CompletableFuture<Void> createMilestone(Milestone milestone) {

        return CompletableFuture.supplyAsync(() -> {
            Connection connection = connectionPool.getConnection();


            try (PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO `" + tablePrefix + "milestones`(`name`, `data`) VALUES (?,?)")) {
                preparedStatement.setString(1, milestone.getMilestoneName());
                preparedStatement.setString(2, gson.toJson(milestone, Milestone.class));

                preparedStatement.execute();
            } catch (SQLException sqlException) {
                sqlException.printStackTrace();
            } finally {
                connectionPool.close(connection);
            }
            return null;
        });
    }

    @SneakyThrows
    @Override
    public CompletableFuture<Void> saveMileStone(Milestone milestone) {


        return CompletableFuture.supplyAsync(() -> {
            Connection connection = connectionPool.getConnection();

            try (PreparedStatement preparedStatement = connection.prepareStatement("UPDATE `" + tablePrefix + "milestones` SET `data`=? WHERE `name`=?")) {
                preparedStatement.setString(1, gson.toJson(milestone, Milestone.class));
                preparedStatement.setString(2, milestone.getMilestoneName());

                preparedStatement.execute();
            } catch (SQLException sqlException) {
                sqlException.printStackTrace();
            } finally {
                connectionPool.close(connection);
            }

            return null;
        });
    }

    @Override
    public CompletableFuture<List<Milestone>> getMilestones() {

        return CompletableFuture.supplyAsync(() -> {
            Connection connection = connectionPool.getConnection();
            List<Milestone> milestones = new ArrayList<>();

            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM `" + tablePrefix + "milestones`")) {
                ResultSet resultSet = preparedStatement.executeQuery();

                while (resultSet.next()) {
                    milestones.add(gson.fromJson(resultSet.getString("data"), Milestone.class));
                }

                resultSet.close();
            } catch (SQLException sqlException) {
                sqlException.printStackTrace();
            } finally {
                connectionPool.close(connection);
            }

            return milestones;
        });
    }

    @Override
    public CompletableFuture<Void> createRepeatingMilestone(RepeatingMilestone milestone) {

        return CompletableFuture.supplyAsync(() -> {
            Connection connection = connectionPool.getConnection();

            try (PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO `" + tablePrefix + "repeating_milestones`(`name`, `data`) VALUES (?,?)")) {
                preparedStatement.setString(1, milestone.getMilestoneName());
                preparedStatement.setString(2, gson.toJson(milestone, RepeatingMilestone.class));

                preparedStatement.execute();
            } catch (SQLException sqlException) {
                sqlException.printStackTrace();
            } finally {
                connectionPool.close(connection);
            }

            return null;
        });
    }

    @Override
    public CompletableFuture<Void> saveRepeatingMileStone(RepeatingMilestone milestone) {

        return CompletableFuture.supplyAsync(() -> {
            Connection connection = connectionPool.getConnection();

            try (PreparedStatement preparedStatement = connection.prepareStatement("UPDATE `" + tablePrefix + "repeating_milestones` SET `data`=? WHERE `name`=?")) {
                preparedStatement.setString(1, gson.toJson(milestone, RepeatingMilestone.class));
                preparedStatement.setString(2, milestone.getMilestoneName());

                preparedStatement.execute();

            } catch (SQLException sqlException) {
                sqlException.printStackTrace();
            } finally {
                connectionPool.close(connection);
            }

            return null;
        });
    }

    @Override
    public CompletableFuture<List<RepeatingMilestone>> getRepeatingMilestones() {

        return CompletableFuture.supplyAsync(() -> {
            Connection connection = connectionPool.getConnection();
            List<RepeatingMilestone> milestones = new ArrayList<>();

            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM `" + tablePrefix + "repeating_milestones`")){
                ResultSet resultSet = preparedStatement.executeQuery();

                while (resultSet.next()) {
                    milestones.add(gson.fromJson(resultSet.getString("data"), RepeatingMilestone.class));
                }

                resultSet.close();

            } catch (SQLException sqlException) {
                sqlException.printStackTrace();
            } finally {
                connectionPool.close(connection);
            }

            return milestones;
        });
    }

    @SneakyThrows
    @Override
    public CompletableFuture<Void> reset(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Connection connection = connectionPool.getConnection();

            try (PreparedStatement preparedStatement = connection.prepareStatement("UPDATE `" + tablePrefix + "playtime` SET `time`=? WHERE `name` = ?")){
                preparedStatement.setLong(1, 0);
                preparedStatement.setString(2, uuid);

                preparedStatement.execute();

            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                connectionPool.close(connection);
            }

            return null;
        });
    }


}
