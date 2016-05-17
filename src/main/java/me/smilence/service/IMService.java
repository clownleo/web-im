package me.smilence.service;

import com.alibaba.fastjson.JSON;
import com.corundumstudio.socketio.AckCallback;
import com.corundumstudio.socketio.SocketIOClient;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.api.rx.RedisReactiveCommands;
import com.lambdaworks.redis.api.sync.RedisCommands;
import me.smilence.IMError;
import me.smilence.IMEvent;
import me.smilence.MessageType;
import me.smilence.commons.utils.AES;
import me.smilence.commons.utils.RxUtils;
import me.smilence.entity.Group;
import me.smilence.entity.User;
import me.smilence.eventbean.*;
import org.apache.commons.lang3.StringUtils;
import rx.Observable;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static me.smilence.commons.utils.RxUtils.rxAssert;

/**
 * IM service
 * Created by leo on 16-5-8.
 */
public class IMService {
    private static IMService instance = new IMService();

    public static IMService getInstance() {
        return instance;
    }

    private IMService() {
    }

    private static Map<String, SocketIOClient> userOnline = new ConcurrentHashMap<>();
    private static Map<String, Boolean> groupSuspendedStatus = new ConcurrentHashMap<>();
    private static Map<String, String> signatureCache = new ConcurrentHashMap<>();
    private static RedisReactiveCommands<String, String> rxRedis = RedisClient
            .create("redis://localhost")
            .connect()
            .reactive();

    private static RedisCommands<String, String> redis = RedisClient
            .create("redis://localhost")
            .connect()
            .sync();

    public Observable<String> newStamp(SocketIOClient client) {
        String uuid = String.valueOf(UUID.randomUUID());
        client.set("stamp", uuid);
        return Observable.just(uuid);
    }

    public Observable<Boolean> register(SocketIOClient client, RegisterBean bean) {
        return rxRedis
                .exists("user:" + bean.username)
                .doOnNext(num -> rxAssert(num <= 0, IMError.USERNAME_ALREADY_EXIST))
                .flatMap(ignore -> rxRedis.hmset("user:" + bean.username, new User(bean).getMap()))
                .map("OK"::equalsIgnoreCase);

    }

    public Observable<Boolean> auth(SocketIOClient client, LoginBean bean) {
        String stamp = client.<String>get("stamp");
        if (stamp == null)
            return Observable.create(subscriber -> subscriber.onError(IMError.INVALIDATE_REQUEST));

        return rxRedis.hget("user:" + bean.username, "keySign")
                .compose(RxUtils.supportNull(IMError.USERNAME_NOT_EXIST))
                .doOnNext(keySign -> rxAssert(AES.Encrypt(stamp, keySign).equals(bean.signature), IMError.AUTH_FAIL))
                .flatMap(ignore -> rxRedis.hexists("user:" + bean.username, "isSuspended"))
                .doOnNext(isSuspended -> {
                    System.out.println(isSuspended.getClass().getSimpleName());
                    client.set("isSuspended", isSuspended);
                    rxAssert(!isSuspended, IMError.USER_IS_SUSPENDED);
                })
                .doOnNext(ignore -> {
                    client.del("stamp");
                    userOnline.put(bean.username, client);
                    client.set("username", bean.username);
                })
                .map(ignore -> true);
    }

    public Observable<Boolean> logout(SocketIOClient client) {
        boolean result = client.<String>get("username") != null &&
                userOnline.remove(client.<String>get("username")) != null;
        client.del("username");
        return Observable.just(result);
    }

    protected void suspendUser(String username) {
        if (userOnline.containsKey("username")) {
            sendMessage(new MessageBean(
                    null,
                    username,
                    null,
                    new Date(),
                    MessageType.NOTIFICATION,
                    "you are suspended"
            )).subscribe();
            logout(userOnline.get(username)).subscribe();
        }
    }

    protected void changeGroupSuspended(String group) {
        groupSuspendedStatus.remove(group);
    }

    private Observable<Boolean> isGroupSuspended(String group) {
        if (groupSuspendedStatus.containsKey(group))
            return Observable.just(groupSuspendedStatus.get(group));

        return rxRedis.hexists("group:" + group, "isSUspended")
                .doOnNext(isSupended -> groupSuspendedStatus.put(group, isSupended));
    }

    public Observable<Boolean> setKeyBak(SocketIOClient client, KeyBckBean bean) {
        LoginBean loginBean = new LoginBean();
        loginBean.username = bean.username;
        loginBean.signature = bean.signature;
        return auth(client, loginBean)
                .flatMap(success ->
                                rxRedis.hset("user:" + bean.username, "keyEncryptedBck", bean.keyEncryptedBck)
                );
    }

    public Observable<Boolean> resetKeyEncrypted(SocketIOClient client, ResetKeyEncBean bean) {
        LoginBean loginBean = new LoginBean();
        loginBean.username = bean.username;
        loginBean.signature = bean.signature;
        return auth(client, loginBean)
                .flatMap(success ->
                                rxRedis.hset("user:" + bean.username, "keyEncrypted", bean.keyEncrypted)
                );
    }

    public Observable<String> getKeyEncryptedBck(SocketIOClient client, String username) {
        return rxRedis.hget("user:" + username, "keyEncryptedBck");
    }

    public Observable<String> getKeyEncrypted(SocketIOClient client, String username) {
        return rxRedis.hget("user:" + username, "keyEncrypted")
                .compose(RxUtils.supportNull(IMError.USERNAME_NOT_EXIST));
    }

    public Observable<String> getPublicKey(SocketIOClient client, String username) {
        return Observable
                .zip(
                        rxRedis.hget("user:" + username, "publicKey"),
                        getSignature(client),
                        AES::Encrypt
                )
                .compose(RxUtils.supportNull(IMError.USERNAME_NOT_EXIST));
    }

    public Observable<String> getChatKey(SocketIOClient client, String username) {
        return rxGetUsername(client)
                .flatMap(myName -> rxRedis.get(username + ":chat:" + myName))
                .compose(RxUtils.supportNull(IMError.TARGET_NOT_EXIST));
    }

    public Observable<Boolean> setChatKey(SocketIOClient client, SetChatKeyBean bean) {
        return rxGetUsername(client)
                .flatMap(myName -> rxRedis.set(myName + ":chat:" + bean.toUser, bean.chatKey))
                .compose(RxUtils.supportNull(IMError.USERNAME_NOT_EXIST))
                .map("OK"::equalsIgnoreCase);
    }

    public Observable<Boolean> updateUserInfo(SocketIOClient client, Object info) {
        return rxGetUsername(client)
                .flatMap(myName -> rxRedis.hset("user:" + myName, "info", JSON.toJSONString(info)));

    }

    public Observable<Boolean> send2Friend(SocketIOClient client, MessageBean bean) {
        bean.type = MessageType.FRIEND_MESSAGE;
        return rxGetUsername(client)
                .flatMap(username -> rxRedis.sismember(username + ":friends", bean.toUser))
                .doOnNext(isMem -> rxAssert(isMem, IMError.INVALIDATE_REQUEST))
                .flatMap(ignore -> sendMessage(bean));
    }

    public Observable<Boolean> send2GroupMember(SocketIOClient client, MessageBean bean) {
        bean.type = MessageType.GROUP_MESSAGE;
        return isGroupSuspended(bean.group)
                .doOnNext(isSuspended -> rxAssert(isSuspended, IMError.GROUP_IS_SUSPENDED))
                .flatMap(ignore -> rxGetUsername(client))
                .flatMap(myName -> Observable.zip(
                        rxRedis.sismember(bean.group + ":members", myName),
                        rxRedis.sismember(bean.group + ":members", bean.toUser),
                        (aBoolean, aBoolean2) -> aBoolean && aBoolean2
                ))
                .compose(RxUtils.supportNull(IMError.GROUP_NOT_EXIST))
                .doOnNext(success -> rxAssert(success, IMError.INVALIDATE_REQUEST))
                .flatMap(ignore -> sendMessage(bean));
    }

    private Observable<Boolean> broadcast2OnlineMember(MessageBean bean) {
        return rxRedis.smembers(bean.group + ":members")
                .filter(userOnline::containsKey)
                .flatMap(username ->
                                sendMessage(
                                        new MessageBean(
                                                bean.fromUser,
                                                username,
                                                bean.group,
                                                bean.dateTime,
                                                bean.type,
                                                bean.content
                                        )
                                )
                );
    }

    private Observable<Boolean> sendMessage(MessageBean bean) {
//        bean.dateTime = new Date();
//        System.out.println(JSON.toJSONString(bean));
        return rxRedis.exists("user:" + bean.toUser)
                .doOnNext(num -> rxAssert(num > 0, IMError.TARGET_NOT_EXIST))
                .map(ignore -> userOnline.get(bean.toUser))
                .flatMap(client -> {
                    if (client != null) {
                        send(client, IMEvent.MSG_SYNC, bean);
                        return Observable.just(true);
                    } else {
                        return rxRedis.rpush("msg:" + bean.toUser, JSON.toJSONString(bean))
                                .map(aLong -> aLong > 0);
                    }
                });
    }

    public Observable<List<String>> getFriendList(SocketIOClient client) {
        return rxGetUsername(client)
                .flatMap(username -> rxRedis.smembers(username + ":friends"))
                .compose(RxUtils.listSupport());
    }

    public Observable<Object> getUserInfo(SocketIOClient client, String username) {
        return rxRedis.hget("user:" + username, "info")
                .compose(RxUtils.supportNull(IMError.TARGET_NOT_EXIST))
                .map(JSON::parseObject);
    }

    public Observable<Boolean> addFriend(SocketIOClient client, MessageBean bean) {
        bean.type = MessageType.ADD_FRIEND;
        return rxRedis.exists("user:" + bean.toUser)
                .doOnNext(num -> rxAssert(num > 0, IMError.TARGET_NOT_EXIST))
                .flatMap(ignore -> rxGetUsername(client))
                .flatMap(username -> rxRedis.sadd(username + ":addFriend", bean.toUser))
                .map(aLong1 -> aLong1 > 0)
                .doOnNext(aBoolean -> {
                    MessageBean notice = new MessageBean(
                            client.<String>get("username"),
                            bean.toUser,
                            null,
                            new Date(),
                            MessageType.ADD_FRIEND,
                            bean.content
                    );
                    sendMessage(notice).subscribe();
                });
    }

    public Observable<Boolean> removeFriend(SocketIOClient client, String friend) {
        return rxGetUsername(client)
                .flatMap(myName -> Observable.zip(
                        rxRedis.srem(myName + ":friends", friend),
                        rxRedis.srem(friend + ":friends", myName),
                        (num1, num2) -> num1 > 0 && num2 > 0
                ))
                .doOnNext(success -> rxAssert(success, IMError.TARGET_NOT_EXIST))
                .doOnNext(aBoolean -> {
                    MessageBean notice = new MessageBean(
                            client.<String>get("username"),
                            friend,
                            null,
                            new Date(),
                            MessageType.DELETE_FRIEND,
                            null
                    );
                    sendMessage(notice).subscribe();
                });
    }

    public Observable<Boolean> addGroup(SocketIOClient client, GroupBean bean) {

        return rxRedis.exists("group:" + bean.groupName)
                .doOnNext(isExist -> rxAssert(isExist <= 0, IMError.GROUP_NAME_ALREADY_EXIST))
                .flatMap(ignore -> rxGetUsername(client))
                .map(username -> {
                    Group group = new Group(bean);
                    group.owner = username;
                    return group;
                })
                .flatMap(group -> Observable.zip(
                        rxRedis.hmset("group:" + group.groupName, group.getMap()),
                        rxRedis.sadd(group.owner + ":myGroups", group.groupName),
                        rxRedis.sadd(group.groupName + ":members", group.owner),
                        (rs, num1, num2) -> "OK".equals(rs) && num1 > 0 && num2 > 0
                ));
    }

    public Observable<Boolean> replyOfAddFriend(SocketIOClient client, MessageBean bean) {
        String username = client.<String>get("username");

        bean.type = MessageType.REPLY_ADD_FRIEND;
        return rxRedis.srem(bean.toUser + ":addFriend", username)
                .compose(RxUtils.supportNull(IMError.INVALIDATE_REQUEST))
                .doOnNext(num -> rxAssert(num > 0, IMError.INVALIDATE_REQUEST))
                .flatMap(ignore -> {
                    if ("YES".equalsIgnoreCase(bean.content)) {
                        return Observable.zip(
                                rxRedis.sadd(username + ":friends", bean.toUser),
                                rxRedis.sadd(bean.toUser + ":friends", username),
                                (aLong, aLong2) -> true
                        );
                    } else return Observable.just(false);
                })
                .doOnNext(aBoolean -> {
                    MessageBean notice = new MessageBean(
                            client.<String>get("username"),
                            bean.toUser,
                            null,
                            new Date(),
                            MessageType.REPLY_ADD_FRIEND,
                            aBoolean ? "allow add friend" : "reject add friend"
                    );
                    sendMessage(notice).subscribe();
                });
    }

    public Observable<Boolean> joinGroup(SocketIOClient client, MessageBean bean) {
        return Observable
                .zip(
                        rxGetUsername(client),
                        rxRedis.hget("group:" + bean.group, "owner"),
                        (myName, owner) -> {
                            bean.fromUser = myName;
                            bean.toUser = owner;
                            return bean;
                        }
                )
                .compose(RxUtils.supportNull(IMError.TARGET_NOT_EXIST))
                .flatMap(bean1 -> rxRedis.sadd(bean1.fromUser + ":joinGroup", bean1.group))
                .doOnNext(ignore -> {
                    MessageBean notice = new MessageBean(
                            bean.fromUser,
                            bean.toUser,
                            bean.group,
                            new Date(),
                            MessageType.JOIN_GROUP,
                            "join group"
                    );
                    sendMessage(notice).subscribe();
                })
                .map(l -> l > 0);
    }

    public Observable<Boolean> replyOfJoinGroup(SocketIOClient client, MessageBean bean) {
        return Observable
                .zip(
                        rxRedis.hget("group:" + bean.group, "owner"),
                        rxGetUsername(client),
                        (owner, myName) -> {
                            //验证是否群主
                            rxAssert(StringUtils.equals(owner, myName), IMError.INVALIDATE_REQUEST);
                            return null;
                        }
                )
                .flatMap(ignore -> rxRedis.srem(bean.toUser + ":joinGroup", bean.group))
                .compose(RxUtils.supportNull(IMError.INVALIDATE_REQUEST))
                        //判断是否是为有效的入群回复
                .doOnNext(num -> rxAssert(num > 0, IMError.INVALIDATE_REQUEST))
                .flatMap(ignore -> {
                    if ("YES".equalsIgnoreCase(bean.content)) {
                        return Observable.merge(
                                rxRedis.sadd(bean.toUser + ":myGroups", bean.group),
                                rxRedis.sadd(bean.group + ":members", bean.toUser)
                        ).all(rs -> rs > 0
                        );
                    } else
                        return Observable.just(false);
                })
                .doOnNext(aBoolean -> {
                            if (aBoolean) {
                                broadcast2OnlineMember(
                                        new MessageBean(
                                                client.<String>get("username"),
                                                null,
                                                bean.group,
                                                new Date(),
                                                MessageType.REPLY_JOIN_GROUP,
                                                bean.content
                                        )
                                ).subscribe();

                            }
                        }
                );
    }

    /**
     * 获取登录状态的用户的用户名
     */
    private Observable<String> rxGetUsername(SocketIOClient client) {
        return Observable
                .just(client.<String>get("username"))
                .doOnNext(username -> rxAssert(
                        userOnline.containsKey(username),
                        IMError.UNLOGIN));
    }

    /**
     * 发送消息给客户端，伴随两次超时重试的机会
     */
    private <T extends EventBean> void send(SocketIOClient client, String event, T bean) {
        Observable
                .create(
                        subscriber ->
                                client.sendEvent(event, new AckCallback<Object>(Object.class, 5000) {
                                    @Override
                                    public void onSuccess(Object result) {
                                        rxRedis.srem("tmpMsg", JSON.toJSONString(bean)).subscribe();
                                        subscriber.onNext(true);
                                        subscriber.onCompleted();
                                    }

                                    @Override
                                    public void onTimeout() {
                                        super.onTimeout();
                                        subscriber.onError(new TimeoutException());
                                    }
                                }, bean)
                )
                .retry((integer, throwable) -> throwable instanceof TimeoutException && integer < 3)
                .subscribe();
    }

    private boolean msgQueueStarted = false;

    public Observable<List<String>> getMembers(SocketIOClient client, String group) {
        return rxGetUsername(client)
                .flatMap(myName -> rxRedis.sismember(group + ":members", myName))
                .doOnNext(isMember -> rxAssert(isMember, IMError.INVALIDATE_REQUEST))
                .flatMap(ignore -> rxRedis.smembers(group + ":members"))
                .compose(RxUtils.listSupport());
    }

    public Observable<List<String>> getGroups(SocketIOClient client) {
        return rxGetUsername(client)
                .flatMap(username -> rxRedis.smembers(username + ":myGroups"))
                .compose(RxUtils.listSupport());
    }

    public Observable<Boolean> exitGroup(SocketIOClient client, String group) {
        return rxGetUsername(client)
                .flatMap(
                        myName -> Observable.zip(
                                rxRedis.srem(myName + ":myGroups", group),
                                rxRedis.srem(group + ":members", myName),
                                (num1, num2) -> num1 > 0 && num2 > 0
                        )
                ).compose(RxUtils.supportNull(IMError.INVALIDATE_REQUEST))
                .doOnNext(aBoolean ->
                        rxRedis.hget("group:" + group, "owner")
                                .flatMap(owner -> sendMessage(
                                        new MessageBean(
                                                client.<String>get("uername"),
                                                owner,
                                                group,
                                                new Date(),
                                                MessageType.EXIT_GROUP,
                                                "exit group")))
                                .subscribe());
    }

    public Observable<Object> getGroupInfo(SocketIOClient client, String group) {
        return rxRedis.hget("groups:" + group, "info")
                .compose(RxUtils.supportNull(IMError.GROUP_NOT_EXIST))
                .map(JSON::parseObject);
    }

    public Observable<Boolean> updateGroupInfo(SocketIOClient client, UpdateGroupInfoBean ginfo) {
        return Observable
                .zip(
                        rxGetUsername(client),
                        rxRedis.hget("group:" + ginfo.group, "owner"),
                        StringUtils::equals
                ).compose(RxUtils.supportNull(IMError.INVALIDATE_REQUEST))
                .flatMap(ignore -> rxRedis.hset("group:" + ginfo.group, "info", JSON.toJSONString(ginfo.info)));
    }

    public Observable<Boolean> removeGroupMember(SocketIOClient client, RemoveGroupMemberBean bean) {
        return Observable
                .zip(
                        rxGetUsername(client),
                        rxRedis.hget("group:" + bean.group, "owner"),
                        StringUtils::equals
                ).compose(RxUtils.supportNull(IMError.INVALIDATE_REQUEST))
                .flatMap(ignore -> Observable.zip(
                        rxRedis.srem(bean.group + ":members", bean.member),
                        rxRedis.srem(bean.member + "myGroups", bean.group),
                        (num1, num2) -> num1 > 0 && num2 > 0
                ))
                .doOnNext(success -> rxAssert(success, IMError.TARGET_NOT_EXIST))
                .doOnNext(aBoolean -> {
                            broadcast2OnlineMember(
                                    new MessageBean(
                                            client.<String>get("username"),
                                            null,
                                            bean.group,
                                            new Date(),
                                            MessageType.DELETE_GROUP_MEMBER,
                                            null
                                    )
                            ).subscribe();
                        }

                );
    }

    private Observable<String> getSignature(SocketIOClient client) {
        return rxGetUsername(client)
                .flatMap(myName -> {
                    if (signatureCache.containsKey(myName))
                        return Observable.just(signatureCache.get(myName));
                    return rxRedis.hget("user:" + myName, "keySign");
                });
    }

    public void startMsgQueue() {
        if (msgQueueStarted) return;
        Observable.interval(10, TimeUnit.MILLISECONDS)
                .map(ignore -> redis.lrange("msgQueue", 0, 999))
                .doOnNext(list -> redis.ltrim("msgQueue", list.size(), -1))
                .flatMap(Observable::from)
                .map(s -> JSON.<MessageBean>parseObject(s, MessageBean.class))
                .doOnNext((bean) -> sendMessage(bean).subscribe())
                .doOnError(Throwable::printStackTrace)
                .doOnError(throwable -> restartMsgQueue())
                .subscribe();
    }

    private void restartMsgQueue() {
        msgQueueStarted = false;
        startMsgQueue();
    }

    private boolean offlineQueueStarted = false;

    public void startOfflineQueue() {
        if (offlineQueueStarted) return;
        Observable.interval(10, TimeUnit.MILLISECONDS)
                .map(ignore -> userOnline.values())
                .flatMap(Observable::from)
                .map(client -> client.<String>get("username"))
                .filter(StringUtils::isNoneEmpty)
                .doOnNext(username -> {
                    List<String> list = redis.lrange("msg:" + username, 0, 999);
                    if (list.size() > 0) {
                        redis.ltrim("msg:" + username, list.size(), -1);
                        redis.rpush("msgQueue", list.toArray(new String[list.size()]));
                    }
                })
                .doOnError(Throwable::printStackTrace)
                .doOnError(throwable -> restartOfflineQueue())
                .subscribe();
    }


    private void restartOfflineQueue() {
        offlineQueueStarted = false;
        startOfflineQueue();
    }

    public static void main(String[] args) throws InterruptedException {
//        Aes256.encrypt()
//        Map<String, String> m = new HashMap<>(1000000);
        long start = System.currentTimeMillis();
//        Observable.range(1, 1000000)
//                .map(String::valueOf)
//                .collect(() -> m, (map, s) -> map.put(s, s))
//                .doOnNext(stringStringMap ->
//                                System.out.printf("cost: %f s", (System.currentTimeMillis() - start) / 1000.0)
//                ).subscribe();
//        Stream.iterate(1, integer -> integer + 1)
//                .limit(1000000)
//                .map(String::valueOf)
//                .collect(Collectors.toMap(s -> s, s -> s));
//        System.out.printf("cost: %f s", (System.currentTimeMillis() - start) / 1000.0);

        Observable.interval(0, TimeUnit.MILLISECONDS)
                .map(ignore -> redis.lrange("x", 0, 9))
                .doOnNext(list -> redis.ltrim("x", list.size(), -1))
                .flatMap(Observable::from)
                .doOnNext(System.out::println)
                .doOnCompleted(() -> {
                    System.out.printf("cost: %f s", (System.currentTimeMillis() - start) / 1000.0);
                    System.exit(0);
                })
                .subscribe();
//        Observable.range(1, 100000)
//                .map(i -> redis.rpush("x", String.valueOf(i)))
//                .subscribe(new Subscriber<Long>() {
//                    @Override
//                    public void onCompleted() {
//                        System.out.printf("cost: %f s", (System.currentTimeMillis() - start) / 1000.0);
//                        System.exit(0);
//                    }
//
//                    @Override
//                    public void onError(Throwable e) {
//
//                    }
//
//                    @Override
//                    public void onNext(Long aLong) {
//
//                    }
//                });

        Thread.currentThread().join();

    }
}
