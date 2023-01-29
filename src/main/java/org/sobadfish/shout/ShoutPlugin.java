package org.sobadfish.shout;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.Listener;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.TextFormat;
import com.google.common.base.Throwables;
import com.google.gson.Gson;
import org.sobadfish.shout.configs.MsgConfig;
import org.sobadfish.shout.configs.ShoutConfig;
import org.sobadfish.shout.from.WindowsFrom;
import org.sobadfish.shout.money.MoneyManager;
import org.sobadfish.shout.socket.SocketManager;
import org.sobadfish.shout.utils.TextUtils;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/*
 _   _   _   _____   _   _   _____   _   _   _____
| | | | | | /  ___/ | | | | /  _  \ | | | | |_   _|
| | | | | | | |___  | |_| | | | | | | | | |   | |
| | | | | | \___  \ |  _  | | | | | | | | |   | |
| |_| | | |  ___| | | | | | | |_| | | |_| |   | |
\_____/ |_| /_____/ |_| |_| \_____/ \_____/   |_|
*
* */
public class ShoutPlugin extends PluginBase implements Listener {

    public static final String TITLE = "&7[&6UI-SHOUT&7] ";

    public static String SERVER_NAME;

    private static ShoutPlugin shoutPlugin;

    private MsgConfig msgConfig;

    public SocketManager socketManager;

    public MoneyManager moneyManager;

    private ShoutConfig shoutConfig;

    private WindowsFrom windowsFrom;

    @Override
    public void onEnable() {
        this.getLogger().info("正在加载全服喊话");
        shoutPlugin = this;
        saveDefaultConfig();
        reloadConfig();
        getConfig();
        this.getLogger().info(TextFormat.AQUA+" _   _   _   _____   _   _   _____   _   _   _____  ");
        this.getLogger().info(TextFormat.AQUA+"| | | | | | /  ___/ | | | | /  _  \\ | | | | |_   _| ");
        this.getLogger().info(TextFormat.AQUA+"| | | | | | | |___  | |_| | | | | | | | | |   | |   ");
        this.getLogger().info(TextFormat.AQUA+"| | | | | | \\___  \\ |  _  | | | | | | | | |   | |   ");
        this.getLogger().info(TextFormat.AQUA+"| |_| | | |  ___| | | | | | | |_| | | |_| |   | |   ");
        this.getLogger().info(TextFormat.AQUA+"\\_____/ |_| /_____/ |_| |_| \\_____/ \\_____/   |_|   \n\n");
        this.getLogger().info("初始化配置文件");
        initConfig();
        this.getLogger().info("启动检查网络环境");
        initNetWork();

        moneyManager = MoneyManager.getManager();
        initFrom();
        this.getServer().getPluginManager().registerEvents(windowsFrom,this);


    }

    private void initFrom() {
        windowsFrom = new WindowsFrom();
        windowsFrom.setFromDataListener((player, data) -> {
            String msg = data.msg;
            if("无色彩".equalsIgnoreCase(data.color)){
                msg = TextUtils.clearColor(msg);
            }
            float money = shoutConfig.rate.msg * TextUtils.mathLine(msg);
            switch (data.color.toLowerCase()){
                case "自定义":
                    money *= shoutConfig.rate.custom;
                    break;
                case "§d随机颜色":
                    money *= shoutConfig.rate.random;
                    msg = TextUtils.roundColor(msg);
                    break;
                default:break;
            }
            money *= shoutConfig.money;
            if(moneyManager.myMoney(player.getName()) > money){
                moneyManager.reduceMoney(player.getName(),money);
                player.sendMessage(TextFormat.colorize('&',TITLE+" &2成功花费 &7"+money+" &2进行了一次喊话"));
            }else{
                player.sendMessage(TextFormat.colorize('&',TITLE+" &4您的金钱不足"));
                return;
            }
            MsgBroadcastData data1 = new MsgBroadcastData();
            data1.server = SERVER_NAME;
            data1.player = player.getName();
            data1.msg = msg;
            data1.type = data.type;
            if(socketManager != null){
                socketManager.sendMessage(data1);
            }
//            if(socketManager == null || !socketManager.enable || socketManager.getType() == SocketManager.SocketType.SERVER){
            broadcastMessage(data1);

        });
    }

    private void initConfig() {
        SERVER_NAME = getConfig().getString("server.name");
        saveResource("msg.json");
        saveResource("shout.json");
        msgConfig = fileToClass(new File(this.getDataFolder()+"/msg.json"),MsgConfig.class);
        shoutConfig = fileToClass(new File(this.getDataFolder()+"/shout.json"),ShoutConfig.class);
    }

    public void setMoneyManager(MoneyManager moneyManager) {
        this.moneyManager = moneyManager;
    }

    public static ShoutPlugin getShoutPlugin() {
        return shoutPlugin;
    }

    public MsgConfig getMsgConfig() {
        return msgConfig;
    }

    public ShoutConfig getShoutConfig() {
        return shoutConfig;
    }

    public void initNetWork(){

        String host = getConfig().getString("server.host","127.0.0.1");
        int port = getConfig().getInt("port",25633);
        socketManager = SocketManager.connectManager(host,port);
        if(socketManager != null){
            this.getLogger().info("网络环境检查完成 当前连接类型为 "+TextFormat.colorize('&',"&e"+socketManager.getType()+""));
            socketManager.setConnectListener(new SocketManager.SocketConnectListener() {
                @Override
                public void join(SocketManager.SocketNode socket) {
                    getLogger().info(socket.getIPAddress()+":"+socket.getPort()+" 已连接");
                }

                @Override
                public void quit(SocketManager.SocketNode socket) {
                    getLogger().info(socket.getIPAddress()+":"+socket.getPort()+" 已断开");

                }
            });
            socketManager.setDataListener((socketManager, messageData) -> {
                MsgBroadcastData data = messageData.getData(MsgBroadcastData.class);
                broadcastMessage(data);
            });
        }



    }



    public static class MsgBroadcastData{
        public String server;

        public String msg;

        public String type;

        public String player;
    }

    public void broadcastMessage(MsgBroadcastData data){

        String msg = TextFormat.colorize('&',shoutConfig.msg.echo
                .replace("${server}",data.server)
                .replace("${player}",data.player));
        msg = msg.replace("${msg}",data.msg);
        String finalMsg = msg;
        switch (data.type.toLowerCase()){
            case "msg":
                Server.getInstance().broadcastMessage(msg);
                break;
            case "tip":
                Server.getInstance().getOnlinePlayers().values().forEach(player1 -> player1.sendTip(finalMsg));
                break;
            case "popup":
                Server.getInstance().getOnlinePlayers().values().forEach(player1 -> player1.sendPopup(finalMsg));
                break;
            case "title":
                Server.getInstance().getOnlinePlayers().values().forEach(player1 -> player1.sendTitle(finalMsg));
                break;
                default:break;
        }
    }

    private <T> T fileToClass(File file,Class<T> tClass) {
        Gson gson = new Gson();
        InputStreamReader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            return gson.fromJson(reader,tClass);
        } catch (IOException e) {
            this.getLogger().error("无法读取 "+file.getName()+" 配置文件");
            e.printStackTrace();
        }finally {
            if(reader !=null){
                try {
                    reader.close();
                } catch (IOException e) {
                    this.getLogger().error(Throwables.getStackTraceAsString(e));
                }
            }
        }
        return null;
    }


    @Override
    public void onDisable() {
        if(socketManager != null){
            socketManager.disable();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(sender.isPlayer()){

            windowsFrom.displayWindow((Player)sender,shoutConfig);


        }else{
            if(args.length > 0){
                MsgBroadcastData data1 = new MsgBroadcastData();
                data1.server = SERVER_NAME;
                data1.player = "CONSOLE";
                data1.msg = args[0];
                data1.type = "Msg";
                if(socketManager != null){
                    socketManager.sendMessage(data1);
                }
//            if(socketManager == null || !socketManager.enable || socketManager.getType() == SocketManager.SocketType.SERVER){
                broadcastMessage(data1);
            }else {
                sender.sendMessage(TextFormat.colorize('&', TITLE + "请不要在控制台执行"));
            }
        }
        return super.onCommand(sender, command, label, args);
    }


}


