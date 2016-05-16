package me.smilence;

import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.ExceptionListener;
import io.netty.channel.ChannelHandlerContext;
import me.smilence.commons.utils.InfoMonitor;
import me.smilence.commons.utils.properties.PropertiesLoader;
import me.smilence.eventbean.*;
import me.smilence.service.IMService;
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

        imService = IMService.getInstance();
        bindEvent();
    }

    public void bindEvent() {
        socketIOServer.addEventListener(
                IMEvent.NEW_STAMP ,
                Object.class,
                (BaseListener<Object>) (client, data, ackSender) -> {
                    imService.newStamp(client)
                            .subscribe(
                                    stamp -> ackSender.sendAckData(0, stamp),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

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
                IMEvent.SET_KEY_BCK,
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
                IMEvent.RESET_KEY,
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
                IMEvent.GET_KET_ENCRYPTED,
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
                IMEvent.GET_KET_ENCRYPTED_bck,
                String.class,
                (BaseListener<String>) (client, data, ackSender) -> {
                    imService.getKeyEncryptedBck(client, data)
                            .subscribe(
                                    result -> ackSender.sendAckData(0, result),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                IMEvent.ADD_FRIEND,
                MessageBean.class,
                (BaseListener<MessageBean>) (client, data, ackSender) -> {
                    imService.addFriend(client, data)
                            .subscribe(
                                    ignore -> ackSender.sendAckData(0),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                IMEvent.REPLY_OF_ADD_FRIEND,
                MessageBean.class,
                (BaseListener<MessageBean>) (client, data, ackSender) -> {
                    imService.replyOfAddFriend(client, data)
                            .subscribe(
                                    ignore -> ackSender.sendAckData(0),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                IMEvent.REMOVE_FRIEND,
                String.class,
                (BaseListener<String>) (client, data, ackSender) -> {
                    imService.removeFriend(client, data)
                            .subscribe(
                                    ignore -> ackSender.sendAckData(0),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                IMEvent.SEND_TO_FRIEND,
                MessageBean.class,
                (BaseListener<MessageBean>) (client, data, ackSender) -> {
                    imService.send2Friend(client, data)
                            .subscribe(
                                    friends -> ackSender.sendAckData(0),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                IMEvent.GET_FRIENDS,
                Object.class,
                (BaseListener<Object>) (client, data, ackSender) -> {
                    imService.getFriendList(client)
                            .subscribe(
                                    friends -> ackSender.sendAckData(0, friends),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                IMEvent.JOIN_GROUP,
                MessageBean.class,
                (BaseListener<MessageBean>) (client, data, ackSender) -> {
                    imService.joinGroup(client, data)
                            .subscribe(
                                    ignore -> ackSender.sendAckData(0),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                IMEvent.REPLY_OF_JOIN_GROUP,
                MessageBean.class,
                (BaseListener<MessageBean>) (client, data, ackSender) -> {
                    imService.replyOfJoinGroup(client, data)
                            .subscribe(
                                    ignore -> ackSender.sendAckData(0),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                IMEvent.ADD_GROUP,
                GroupBean.class,
                (BaseListener<GroupBean>) (client, data, ackSender) -> {
                    imService.addGroup(client, data)
                            .subscribe(
                                    ignore -> ackSender.sendAckData(0),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                IMEvent.GET_GROUP_INFO,
                String.class,
                (BaseListener<String>) (client, data, ackSender) -> {
                    imService.getGroupInfo(client, data)
                            .subscribe(
                                    info -> ackSender.sendAckData(0, info),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                IMEvent.GET_CHAT_KEY,
                String.class,
                (BaseListener<String>) (client, username, ackSender) -> {
                    imService.getChatKey(client, username)
                            .subscribe(
                                    chatKey -> ackSender.sendAckData(0, chatKey),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                IMEvent.SET_CHAT_KEY,
                SetChatKeyBean.class,
                (BaseListener<SetChatKeyBean>) (client, bean, ackSender) -> {
                    imService.setChatKey(client, bean)
                            .subscribe(
                                    ignore -> ackSender.sendAckData(0),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                IMEvent.GET_PUBLIC_KEY,
                String.class,
                (BaseListener<String>) (client, bean, ackSender) -> {
                    imService.getPublicKey(client, bean)
                            .subscribe(
                                    pubKey -> ackSender.sendAckData(0, pubKey),
                                    throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                            );
                }
        );

        socketIOServer.addEventListener(
                IMEvent.GET_GROUPS,
                Object.class,
                (BaseListener<Object>) (client, ignore, ackSender) ->
                        imService.getGroups(client).subscribe(
                                groups -> ackSender.sendAckData(0, groups),
                                throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                        )
        );

        socketIOServer.addEventListener(
                IMEvent.GET_GROUP_MEMBERS,
                String.class,
                (BaseListener<String>) (client, groupName, ackSender) ->
                        imService.getMembers(client, groupName).subscribe(
                                members -> ackSender.sendAckData(0, members),
                                throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                        )
        );

        socketIOServer.addEventListener(
                IMEvent.EXIT_GROUP,
                String.class,
                (BaseListener<String>) (client, groupName, ackSender) ->
                        imService.exitGroup(client, groupName).subscribe(
                                ignore -> ackSender.sendAckData(0),
                                throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                        )
        );

        socketIOServer.addEventListener(
                IMEvent.REMOVE_GROUP_MEMBER,
                RemoveGroupMemberBean.class,
                (BaseListener<RemoveGroupMemberBean>) (client, bean, ackSender) ->
                        imService.removeGroupMember(client, bean).subscribe(
                                ignore -> ackSender.sendAckData(0),
                                throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                        )
        );

        socketIOServer.addEventListener(
                IMEvent.GET_USER_INFO,
                String.class,
                (BaseListener<String>) (client, data, ackSender) ->
                        imService.getUserInfo(client, data).subscribe(
                                info -> ackSender.sendAckData(0, info),
                                throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                        )
        );

        socketIOServer.addEventListener(
                IMEvent.GET_GROUP_INFO,
                String.class,
                (BaseListener<String>) (client, data, ackSender) ->
                        imService.getGroupInfo(client, data).subscribe(
                                info -> ackSender.sendAckData(0, info),
                                throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                        )
        );

        socketIOServer.addEventListener(
                IMEvent.UPDATE_USER_INFO,
                Object.class,
                (BaseListener<Object>) (client, data, ackSender) ->
                        imService.updateUserInfo(client, data).subscribe(
                                ignore -> ackSender.sendAckData(0),
                                throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                        )
        );

        socketIOServer.addEventListener(
                IMEvent.UPDATE_GROUP_INFO,
                UpdateGroupInfoBean.class,
                (BaseListener<UpdateGroupInfoBean>) (client, data, ackSender) ->
                        imService.updateGroupInfo(client, data).subscribe(
                                ignore -> ackSender.sendAckData(0),
                                throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                        )
        );

        socketIOServer.addEventListener(
                IMEvent.SEND_TO_GROUP_MEMBER,
                MessageBean.class,
                (BaseListener<MessageBean>) (client, data, ackSender) ->
                        imService.send2GroupMember(client, data).subscribe(
                                ignore -> ackSender.sendAckData(0),
                                throwable -> ackSender.sendAckData(((IMError) throwable).getCode())
                        )
        );

        socketIOServer.addDisconnectListener(imService::logout);

        imService.startMsgQueue();
        imService.startOfflineQueue();
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
            if(!(data instanceof EventBean) || ((EventBean)data).validate()){
                on(client, data, ackSender);
            } else ackSender.sendAckData(IMError.FORMAT_ERROR.getCode());

        }
    }
}