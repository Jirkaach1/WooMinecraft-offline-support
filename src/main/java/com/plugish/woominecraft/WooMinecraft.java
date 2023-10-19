
package com.plugish.woominecraft;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plugish.woominecraft.pojo.Order;
import com.plugish.woominecraft.pojo.WMCPojo;
import com.plugish.woominecraft.pojo.WMCProcessedOrders;
import okhttp3.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Sound;

public final class WooMinecraft extends JavaPlugin {

    static WooMinecraft instance;

    private YamlConfiguration l10n;

    public YamlConfiguration logConfig;
    private File logFile;

    public static final String NL = System.getProperty("line.separator");

    /**
     * Stores the player data to prevent double checks.
     * <p>
     * i.e. name:true|false
     */
    private List<String> PlayersMap = new ArrayList<>();

    @Override
    public void onEnable() {
        instance = this;
        String asciiArt =

                "  \n" +
                "  \n" +
                "  \n" +
                        " _       __                 __ __    ______                    ____     ____    ______\n" +
                        "  | |     / /  ____   ____   / // /   / ____/  __  __   ____    / __ \\   / __ \\  / ____/\n" +
                        "  | | /| / /  / __ \\ / __ \\ / // /_  / /_     / / / /  / __ \\  / /_/ /  / /_/ / / / __  \n" +
                        "  | |/ |/ /  / /_/ // /_/ //__  __/ / __/    / /_/ /  / / / / / _, _/  / ____/ / /_/ /  \n" +
                        "  |__/|__/   \\____/ \\____/   /_/   /_/       \\__,_/  /_/ /_/ /_/ |_|  /_/      \\____/\n"
                +   "  \n"
                        +   "  \n"
                        +   "  \n";
        getLogger().info(asciiArt);

        this.logFile = new File(this.getDataFolder(), "log.yml");
        this.logConfig = YamlConfiguration.loadConfiguration(logFile);
        if (this.logConfig != null) {
            this.logConfig.set("loggingEnabled", true);
            try {
                this.logConfig.save(this.logFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        YamlConfiguration config = (YamlConfiguration) getConfig();

        try {
            saveDefaultConfig();
        } catch (IllegalArgumentException e) {
            getLogger().warning(e.getMessage());
        }

        String lang = getConfig().getString("lang");
        if (lang == null) {
            getLogger().warning("No default l10n set, setting to English.");
        }

        // Load the commands.
        getCommand("woo").setExecutor(new WooCommand());

        // Log when the plugin is initialized.
        getLogger().info(this.getLang("log.com_init"));

        BukkitRunner scheduler = new BukkitRunner(instance);
        scheduler.runTaskTimerAsynchronously(instance, config.getInt( "update_interval" ) * 20, config.getInt( "update_interval" ) * 20 );

        // Log when the plugin is fully enabled (setup complete).
        getLogger().info(this.getLang("log.enabled"));
    }

    @Override
    public void onDisable() {
        // Disable logging on plugin shutdown
        if (this.logConfig != null) {
            this.logConfig.set("loggingEnabled", false);
            try {
                this.logConfig.save(this.logFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Log when the plugin is fully shut down.
        getLogger().info(this.getLang("log.com_init"));
    }

    // Helper method to get localized strings
    String getLang(String path) {
        if (null == this.l10n) {
            LangSetup lang = new LangSetup(instance);
            l10n = lang.loadConfig();
        }
        return this.l10n.getString(path);
    }

    // Validates the basics needed in the config.yml file.
    private void validateConfig() throws Exception {
        if (1 > this.getConfig().getString("url").length()) {
            throw new Exception("Server URL is empty, check config.");
        } else if (this.getConfig().getString("url").equals("http://playground.dev")) {
            throw new Exception("URL is still the default URL, check config.");
        } else if (1 > this.getConfig().getString("key").length()) {
            throw new Exception("Server Key is empty, this is insecure, check config.");
        }
    }

    // Gets the site URL
    public URL getSiteURL() throws Exception {
        boolean usePrettyPermalinks = this.getConfig().getBoolean("prettyPermalinks");
        String baseUrl = getConfig().getString("url") + "/wp-json/wmc/v1/server/";
        if (!usePrettyPermalinks) {
            baseUrl = getConfig().getString("url") + "/index.php?rest_route=/wmc/v1/server/";

            String customRestUrl = this.getConfig().getString("restBasePath");
            if (!customRestUrl.isEmpty()) {
                baseUrl = customRestUrl;
            }
        }
        debug_log("Checking base URL: " + baseUrl);
        return new URL(baseUrl + getConfig().getString("key"));
    }

    // Checks all online players against the website's database looking for pending donation deliveries
    boolean check() throws Exception {
        // Make 100% sure the config has at least a key and url
        this.validateConfig();
        // Contact the server.
        String pendingOrders = getPendingOrders();
        debug_log("Logging website reply" + NL + pendingOrders.substring(0, Math.min(pendingOrders.length(), 64)) + "...");
        // Server returned an empty response, bail here.
        if (pendingOrders.isEmpty()) {
            debug_log("Pending orders are completely empty", 2);
            return false;
        }
        // Create a new object from JSON response.
        Gson gson = new GsonBuilder().create();
        WMCPojo wmcPojo = gson.fromJson(pendingOrders, WMCPojo.class);
        List<Order> orderList = wmcPojo.getOrders();
        // Validate we can indeed process what we need to.
        if (wmcPojo.getData() != null) {
            // We have an error, so we need to bail.
            wmc_log("Code:" + wmcPojo.getCode(), 3);
            throw new Exception(wmcPojo.getMessage());
        }
        if (orderList == null || orderList.isEmpty()) {
            wmc_log("No orders to process.", 2);
            return false;
        }
        // foreach ORDERS in JSON feed
        List<Integer> processedOrders = new ArrayList<>();
        for (Order order : orderList) {
            Player player = getServer().getPlayerExact(order.getPlayer());
            if (null == player) {
                debug_log("Player was null for an order", 2);
                continue;
            }

            // World whitelisting.
            if (getConfig().isSet("whitelist-worlds")) {
                List<String> whitelistWorlds = getConfig().getStringList("whitelist-worlds");
                String playerWorld = player.getWorld().getName();
                if (!whitelistWorlds.contains(playerWorld)) {
                    wmc_log("Player " + player.getDisplayName() + " was in world " + playerWorld +
                            " which is not in the white-list, no commands were run.");
                    continue;
                }
            }

            // Walk over all commands and run them at the next available tick.
            for (String command : order.getCommands()) {
                BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
                scheduler.scheduleSyncDelayedTask(instance, () ->
                        Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), command), 20L);
            }
            debug_log("Adding item to list - " + order.getOrderId());
            processedOrders.add(order.getOrderId());
            debug_log("Processed length is " + processedOrders.size());

            // Log the order
            logOrder(order, player.getName());

            // Notify OP players
            for (Player op : Bukkit.getServer().getOnlinePlayers()) {
                if (op.isOp()) {
                    op.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6[Woo4FunRPG] &fOrder Fulfilled - Player: &6" + player.getName() + "&f, OrderID: &e" + order.getOrderId()));
                }
            }

            // Print to server console
            Bukkit.getServer().getConsoleSender().sendMessage("Order Fulfilled - Player: " + player.getName() + ", OrderID: " + order.getOrderId());

        }
        // If it's empty, we skip it.
        if (processedOrders.isEmpty()) {
            return false;
        }
        // Send/update processed orders.
        return sendProcessedOrders(processedOrders);
    }

    // Sends the processed orders to the site.
    private boolean sendProcessedOrders(List<Integer> processedOrders) throws Exception {
        // Build the GSON data to send.
        Gson gson = new Gson();
        WMCProcessedOrders wmcProcessedOrders = new WMCProcessedOrders();
        wmcProcessedOrders.setProcessedOrders(processedOrders);
        String orders = gson.toJson(wmcProcessedOrders);

        // Setup the client.
        OkHttpClient client = new OkHttpClient();

        // Process stuff now.
        RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), orders);
        Request request = new Request.Builder().url(getSiteURL()).post(body).build();
        Response response = client.newCall(request).execute();

        // If the body is empty, we can do nothing.
        if (null == response.body()) {
            throw new Exception("Received an empty response from your server, check connections.");
        }

        // Get the JSON reply from the endpoint.
        WMCPojo wmcPojo = gson.fromJson(response.body().string(), WMCPojo.class);
        if (null != wmcPojo.getCode()) {
            wmc_log("Received an error when trying to send post data: " + wmcPojo.getCode(), 3);
            throw new Exception(wmcPojo.getMessage());
        }

        return true;
    }

    // If debugging is enabled.
    public boolean isDebug() {
        return getConfig().getBoolean("debug");
    }

    // Gets pending orders from the WordPress JSON endpoint.
    private String getPendingOrders() throws Exception {
        URL baseURL = getSiteURL();
        BufferedReader input = null;
        try {
            Reader streamReader = new InputStreamReader(baseURL.openStream());
            input = new BufferedReader(streamReader);
        } catch (IOException e) { // FileNotFoundException extends IOException, so we just catch that here.
            String key = getConfig().getString("key");
            String msg = e.getMessage();
            if (msg.contains(key)) {
                msg = msg.replace(key, "******");
            }
            wmc_log(msg);
            return "";
        }
        StringBuilder buffer = new StringBuilder();
        // Walk over each line of the response.
        String line;
        while ((line = input.readLine()) != null) {
            buffer.append(line);
        }
        input.close();
        return buffer.toString();
    }

    // Log stuff.
    private void wmc_log(String message) {
        this.wmc_log(message, 1);
    }

    // Logs to the debug log.
    private void debug_log(String message) {
        if (isDebug()) {
            this.wmc_log(message, 1);
        }
    }

    // Logs to the debug log.
    private void debug_log(String message, Integer level) {
        if (isDebug()) {
            this.wmc_log(message, level);
        }
    }

    // Log stuff.
    private void wmc_log(String message, Integer level) {
        if (!isDebug()) {
            return;
        }
        switch (level) {
            case 1:
                this.getLogger().info(message);
                break;
            case 2:
                this.getLogger().warning(message);
                break;
            case 3:
                this.getLogger().severe(message);
                break;
        }
    }

    // Determines if the user is a paid user based on the player's name.
    private boolean isPaidUser(String playerName) {
        String playerKeyBase = playerName + ':';
        String validPlayerKey = playerKeyBase + true;
        // Check if the server is in online mode.
        if (Bukkit.getServer().getOnlineMode()) {
            return true;
        }
        if (!Bukkit.spigot().getConfig().getBoolean("settings.bungeecord")) {

            return true;
        }
        // Check the base pattern, if it exists, return if the player is valid or not.
        // Doing so should save on many if/else statements
        if (PlayersMap.toString().contains(playerKeyBase)) {
            boolean valid = PlayersMap.contains(validPlayerKey);
            if (!valid) {
                // Handle the case where the player is not valid, for example, notify the player.
                // You can customize this part based on your needs.
                // player.sendMessage("You are not a paid player."); // Example message
                return valid;
            }
            return valid;
        }
        // Add the player to the list of valid players and return true.
        PlayersMap.add(validPlayerKey);
        debug_log(PlayersMap.toString());
        return true;
    }

    // Log an order to the log file
    private void logOrder(Order order, String playerName) {
        if (this.logConfig == null) {
            return;
        }
        if (!this.logConfig.getBoolean("loggingEnabled", true)) {
            return;
        }

        // Create a timestamp.
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = dateFormat.format(new Date());

        // Get the log entries from the log.yml file.
        List<String> logEntries = logConfig.getStringList("logEntries");

        // Create a log entry for the current order.
        String logMessage = "Timestamp: " + timestamp + ", Player: " + playerName +
                ", OrderID: " + order.getOrderId() + ", Commands: " + String.join("; ", order.getCommands());

        // Add the log entry to the list.
        logEntries.add(logMessage);

        // Save the updated log entries back to the log.yml file.
        logConfig.set("logEntries", logEntries);

        try {
            logConfig.save(logFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null) {
            // Send a colored message to the player
            String message = ChatColor.translateAlternateColorCodes('&', "&l&e[Woo4FunRPG]&r Your order &l&6" + order.getOrderId() + "&r has been fulfilled!");
            player.sendMessage(message);


            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        }
    }

    public File getLogFile() {
        return logFile;
    }
    public void clearLogEntries() {
        if (this.logConfig != null) {
            this.logConfig.set("logEntries", new ArrayList<>());
            try {
                this.logConfig.save(this.logFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}