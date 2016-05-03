import cn.jpush.commons.utils.properties.PropertiesLoader;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.Transport;
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

    private SocketIOServer bootstrapService;
    private static final int pingTimeoutSec = PropertiesLoader.load("system.properties").getInt("socket-io.pingTimeout");
    private static final int pingIntervalSec = PropertiesLoader.load("system.properties").getInt("socket-io.pingInterval");
    private static final Integer socketIoPort = PropertiesLoader.load("system.properties").getInt("socket-io.port");

    public WebIMBootstrap() {
        init();
    }

    public void init() {
        Configuration config = new Configuration();
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


        this.bootstrapService = new SocketIOServer(config);
        bootstrapService.addConnectListener(socketIOClient -> Log.debug("client[{}] connected", socketIOClient.getSessionId().toString()));
        bootstrapService.addDisconnectListener(socketIOClient -> Log.debug("client[{}] disconnected", socketIOClient.getSessionId().toString()));
        bindEvent();
    }

    public void bindEvent() {
//        bootstrapService.addEventListener(IMEvent.CHANNEL_INIT, ChannelInitPacket.class, (socketIOClient, packet, ackRequest) -> {
//            socketIOClient.sendEvent(IMEvent.ACK, new AckPacket(packet.rid, IMEvent.CHANNEL_INIT));
//            imService.channelInit(socketIOClient, packet);
//        });
    }

    public void  run() {
        bootstrapService.start();
        System.err.println("Web-IM Service start...");
    }

    public static void main(String[] args) {
        new WebIMBootstrap().run();
    }


    /**
     * 获取当前总线程数
     * @return
     */
    public static Thread[] findAllThreads() {
        ThreadGroup group =
                Thread.currentThread().getThreadGroup();
        ThreadGroup topGroup = group;

        // 遍历线程组树，获取根线程组
        while ( group != null ) {
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
        InfoMonitor.addTask(() -> String.format("当前总共线程数%d" , findAllThreads().length));
    }

}