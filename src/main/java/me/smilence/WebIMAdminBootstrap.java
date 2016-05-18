package me.smilence;

import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.ExceptionListener;
import io.netty.channel.ChannelHandlerContext;
import me.smilence.commons.utils.InfoMonitor;
import me.smilence.commons.utils.properties.PropertiesLoader;
import me.smilence.eventbean.*;
import me.smilence.service.AdminService;
import me.smilence.service.IMService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * WebIM 服务启动进程
 * Created by leo on 16-5-3.
 */
public class WebIMAdminBootstrap {
    private static Logger Log = LoggerFactory.getLogger(WebIMAdminBootstrap.class);

    private SocketIOServer socketIOServer;
    private static final int pingTimeoutSec = PropertiesLoader.load("system.properties").getInt("socket-io.pingTimeout");
    private static final int pingIntervalSec = PropertiesLoader.load("system.properties").getInt("socket-io.pingInterval");
    private static final Integer socketIoPort = PropertiesLoader.load("system.properties").getInt("socket-io.admin-port");
    private static AdminService adminService;

    public WebIMAdminBootstrap() {
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

        adminService = AdminService.getInstance();
        bindEvent();
    }

    public void bindEvent() {

        socketIOServer.addEventListener(
                AdminEvent.REMOVE_GROUP,
                AdminGroupBean.class,
                (BaseListener<AdminGroupBean>) (client, data, ackSender) ->
                        adminService.removeGroup(data.group).subscribe(
                                ignore -> ackSender.sendAckData(0),
                                throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                        )
        );

        socketIOServer.addEventListener(
                AdminEvent.REMOVE_USER,
                AdminUserBean.class,
                (BaseListener<AdminUserBean>) (client, data, ackSender) ->
                        adminService.removeUser(data.user).subscribe(
                                ignore -> ackSender.sendAckData(0),
                                throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                        )
        );

        socketIOServer.addEventListener(
                AdminEvent.SUSPEND_GROUP,
                AdminGroupBean.class,
                (BaseListener<AdminGroupBean>) (client, data, ackSender) ->
                        adminService.suspendGroup(data.group).subscribe(
                                ignore -> ackSender.sendAckData(0),
                                throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                        )
        );

        socketIOServer.addEventListener(
                AdminEvent.SUSPEND_USER,
                AdminUserBean.class,
                (BaseListener<AdminUserBean>) (client, data, ackSender) ->
                        adminService.suspendUser(data.user).subscribe(
                                ignore -> ackSender.sendAckData(0),
                                throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                        )
        );

        socketIOServer.addEventListener(
                AdminEvent.UNSUSPEND_GROUP,
                AdminGroupBean.class,
                (BaseListener<AdminGroupBean>) (client, data, ackSender) ->
                        adminService.unSuspendGroup(data.group).subscribe(
                                ignore -> ackSender.sendAckData(0),
                                throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                        )
        );

        socketIOServer.addEventListener(
                AdminEvent.UNSUSPEND_USER,
                AdminUserBean.class,
                (BaseListener<AdminUserBean>) (client, data, ackSender) ->
                        adminService.unSuspendUser(data.user).subscribe(
                                ignore -> ackSender.sendAckData(0),
                                throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                        )
        );

        socketIOServer.addEventListener(
                AdminEvent.NEW_STAMP,
                Object.class,
                (DataListener<Object>) (client, ignore, ackSender) ->
                        adminService.newStamp(client).subscribe(
                                stamp -> ackSender.sendAckData(0, stamp),
                                throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                        )
        );

    }

    public void run() {
        socketIOServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(socketIOServer::stop));
        System.err.println("Web-IM Admin Service start...");
    }

    public static void main(String[] args) {
        new WebIMAdminBootstrap().run();
    }

    interface BaseListener<T extends AdminEventBean> extends DataListener<T> {
        void on(SocketIOClient client, T data, AckRequest ackSender) throws Exception;

        @Override
        default void onData(SocketIOClient client, T data, AckRequest ackSender) throws Exception {
            if(!adminService.validate(client, data.signature))
                ackSender.sendAckData(IMError.AUTH_FAIL.getCode());
            else
                on(client, data, ackSender);
        }
    }
}