package cn.jpush;

import cn.jpush.commons.utils.InfoMonitor;
import cn.jpush.commons.utils.properties.PropertiesLoader;
import cn.jpush.eventbean.*;
import cn.jpush.service.IMService;
import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.ExceptionListener;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * WebIM 服务启动进程
 * Created by leo on 16-5-3.
 */
public class WebIMBootstrap {
    private static Logger Log = LoggerFactory.getLogger(WebIMBootstrap.class);

    private SocketIOServer socketIOServer;
    private static final int pingTimeoutSec = PropertiesLoader.load("system.properties").getInt("socket-io.pingTimeout");
    private static final int pingIntervalSec = PropertiesLoader.load("system.properties").getInt("socket-io.pingInterval");
    private static final Integer socketIoPort = PropertiesLoader.load("system.properties").getInt("socket-io.port");
    private static IMService imService;

    public WebIMBootstrap() {
        init();
    }

    public void init() {
        Configuration config = new Configuration();
        config.setAckMode(AckMode.MANUAL);
        //设置支持的传输方式,建议尽可能只是用websocket
        config.setTransports(Transport.WEBSOCKET, Transport.POLLING);
        config.setAllowCustomRequests(true);
        config.setPort(socketIoPort);
        //ping 30秒超时，3分钟一次
        config.setPingTimeout(pingTimeoutSec);
        config.setPingInterval(pingIntervalSec);

        //TODO 编写完整、独立的异常处理模块
        /**
         * 目前只编写了json反序列化异常的处理模块
         */
        config.setExceptionListener(new ExceptionListener() {
            @Override
            public void onEventException(Exception e, List<Object> args, SocketIOClient client) {
                Log.error("【WebIM】exception:", e);
            }

            @Override
            public void onDisconnectException(Exception e, SocketIOClient client) {
                Log.error("【WebIM】exception:", e);
            }

            @Override
            public void onConnectException(Exception e, SocketIOClient client) {
                Log.error("【WebIM】exception:", e);
            }

            @Override
            public void onMessageException(Exception e, String data, SocketIOClient client) {
                Log.error("【WebIM】exception:", e);
            }

            @Override
            public void onJsonException(Exception e, Object data, SocketIOClient client) {
                Log.error("【WebIM】exception:", e);
            }

            @Override
            public boolean exceptionCaught(ChannelHandlerContext ctx, Throwable e) throws Exception {
                Log.error("【WebIM】exception:", e);
                return false;
            }
        });


        this.socketIOServer = new SocketIOServer(config);
        socketIOServer.addConnectListener(socketIOClient -> Log.debug("client[{}] connected", socketIOClient.getSessionId().toString()));
        socketIOServer.addDisconnectListener(socketIOClient -> Log.debug("client[{}] disconnected", socketIOClient.getSessionId().toString()));

        imService = new IMService();
        bindEvent();
    }

    public void bindEvent() {
//        socketIOServer.addEventListener(IMEvent.CHANNEL_INIT, ChannelInitPacket.class, (socketIOClient, packet, ackRequest) -> {
//            socketIOClient.sendEvent(IMEvent.ACK, new AckPacket(packet.rid, IMEvent.CHANNEL_INIT));
//            imService.channelInit(socketIOClient, packet);
//        });
        socketIOServer.addEventListener(
                IMEvent.REGISTER,
                RegisterBean.class,
                (BaseListener<RegisterBean>) (client, data, ackSender) -> {
                    imService.register(client, data)
                            .subscribe(
                                    success -> ackSender.sendAckData(0),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                IMEvent.LOGIN,
                LoginBean.class,
                (BaseListener<LoginBean>) (client, data, ackSender) -> {
                    imService.auth(client, data)
                            .subscribe(
                                    success -> ackSender.sendAckData(0),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                "set key bck",
                KeyBckBean.class,
                (BaseListener<KeyBckBean>) (client, data, ackSender) -> {
                    imService.setKeyBak(client, data)
                            .subscribe(
                                    success -> ackSender.sendAckData(0),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                "reset key",
                ResetKeyEncBean.class,
                (BaseListener<ResetKeyEncBean>) (client, data, ackSender) -> {
                    imService.resetKeyEncrypted(client, data)
                            .subscribe(
                                    success -> ackSender.sendAckData(0),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                "get key encrypted",
                String.class,
                (BaseListener<String>) (client, data, ackSender) -> {
                    imService.getKeyEncrypted(client, data)
                            .subscribe(
                                    result -> ackSender.sendAckData(0, result),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                "get key encrypted bck",
                String.class,
                (BaseListener<String>) (client, data, ackSender) -> {
                    imService.getKeyEncryptedBck(client, data)
                            .subscribe(
                                    result -> ackSender.sendAckData(0, result),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

//        socketIOServer.addEventListener(
//                IMEvent.LOGIN,
//                LoginBean.class,
//                (BaseListener<LoginBean>)(client, data) -> {
//                    imService.login(data)
//                            .subscribe(success -> {
//                                if(success){
//                                    client.sendEvent(IMEvent.LOGIN, 0);
//                                } else {
//                                    client.sendEvent(IMEvent.LOGIN, 1);
//                                }
//                            });
//                }
//        );
    }

    public void run() {
        socketIOServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(socketIOServer::stop));
        System.err.println("Web-IM Service start...");
    }

    public static void main(String[] args) {
        new WebIMBootstrap().run();
    }


    /**
     * 获取当前总线程数
     */
    public static Thread[] findAllThreads() {
        ThreadGroup group =
                Thread.currentThread().getThreadGroup();
        ThreadGroup topGroup = group;

        // 遍历线程组树，获取根线程组
        while (group != null) {
            topGroup = group;
            group = group.getParent();
        }
        // 激活的线程数加倍
        int estimatedSize = topGroup.activeCount() * 2;
        Thread[] slackList = new Thread[estimatedSize];
        //获取根线程组的所有线程
        int actualSize = topGroup.enumerate(slackList);
        // copy into a list that is the exact size
        Thread[] list = new Thread[actualSize];
        System.arraycopy(slackList, 0, list, 0, actualSize);
        return list;
    }

    static {
        InfoMonitor.addTask(() -> String.format("当前总共线程数%d", findAllThreads().length));
    }

    interface BaseListener<T> extends DataListener<T> {
        void on(SocketIOClient client, T data, AckRequest ackSender) throws Exception;

        @Override
        default void onData(SocketIOClient client, T data, AckRequest ackSender) throws Exception {
            if(data instanceof EventBean && ((EventBean)data).validate()){
                on(client, data, ackSender);
            } else ackSender.sendAckData(IMError.FORMAT_ERROR.getCode());

        }
    }
}