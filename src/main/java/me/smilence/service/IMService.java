package me.smilence.service;

import me.smilence.IMError;
import me.smilence.IMEvent;
import me.smilence.commons.utils.AES;
import me.smilence.commons.utils.RxUtils;
import me.smilence.entity.Group;
import me.smilence.entity.User;
import com.alibaba.fastjson.JSON;
import com.corundumstudio.socketio.AckCallback;
import com.corundumstudio.socketio.SocketIOClient;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.api.rx.RedisReactiveCommands;
import me.smilence.eventbean.*;
import org.apache.commons.lang3.StringUtils;
import rx.Observable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static me.smilence.commons.utils.RxUtils.rxAssert;

/**
 * IM service
 * Created by leo on 16-5-8.
 */
public class IMService {
    private static Map<String, SocketIOClient> userOnline = new ConcurrentHashMap<>();
    private static RedisReactiveCommands<String, String> redis = RedisClient
            .create("redis://localhost")
            .connect()
            .reactive();

    public Observable<Boolean> register(SocketIOClient client, RegisterBean bean) {
        return redis
                .exists("user:" + bean.username)
                .doOnNext(num -> rxAssert(num <= 0, IMError.USERNAME_ALREADY_EXIST))
                .flatMap(ignore -> redis.hmset("user:" + bean.username, new User(bean).getMap()))
                .map("OK"::equalsIgnoreCase);

    }

    public Observable<String> newStamp(SocketIOClient client) {
        String uuid = String.valueOf(UUID.randomUUID());
        client.set("stamp", uuid);
        return Observable.just(uuid);
    }

    public Observable<Boolean> auth(SocketIOClient client, LoginBean bean) {
        String stamp = client.<String>get("stamp");
        if (stamp == null)
            return Observable.create(subscriber -> subscriber.onError(IMError.INVALI_REQUEST));

        return redis.hget("user:" + bean.username, "keySign")
                .compose(RxUtils.suportNull(IMError.USERNAME_NOT_EXIST))
                .doOnNext(keySign -> rxAssert(AES.Encrypt(stamp, keySign).equals(bean.signature), IMError.AUTH_FAIL))
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
        client.del("client");
        return Observable.just(result);
    }

    public Observable<Boolean> setKeyBak(SocketIOClient client, KeyBckBean bean) {
        LoginBean loginBean = new LoginBean();
        loginBean.username = bean.username;
        loginBean.signature = bean.signature;
        return auth(client, loginBean)
                .flatMap(success ->
                                redis.hset("user:" + bean.username, "keyEncryptedBck", bean.keyEncryptedBck)
                );
    }

    public Observable<Boolean> resetKeyEncrypted(SocketIOClient client, ResetKeyEncBean bean) {
        LoginBean loginBean = new LoginBean();
        loginBean.username = bean.username;
        loginBean.signature = bean.signature;
        return auth(client, loginBean)
                .flatMap(success ->
                                redis.hset("user:" + bean.username, "keyEncrypted", bean.keyEncrypted)
                );
    }

    public Observable<String> getKeyEncryptedBck(SocketIOClient client, String username) {
        return redis.hget("user:" + username, "keyEncryptedBck");
    }

    public Observable<String> getKeyEncrypted(SocketIOClient client, String username) {
        return redis.hget("user:" + username, "keyEncrypted")
                .compose(RxUtils.suportNull(IMError.USERNAME_NOT_EXIST));
    }

    public Observable<Boolean> updateUserInfo(SocketIOClient client, UpdateUserInfoBean bean) {
        if (client.<String>get("username") != null && userOnline.containsKey(client.<String>get("username")))
            return Observable.create(subscriber -> subscriber.onError(IMError.UNLOGIN));
        return redis.hset("user:" + client.<String>get("username"), "info", JSON.toJSONString(bean.info));
    }

    public Observable<Boolean> send2Friend(SocketIOClient client, MessageBean bean) {
        String username = client.<String>get("username");
        return redis.sismember(username + ":friends", bean.toUser)
                .doOnNext(isMem -> rxAssert(isMem, IMError.INVALI_REQUEST))
                .flatMap(ignore -> sendMessage(bean));
    }

    private Observable<Boolean> sendMessage(MessageBean bean) {
        bean.dateTime = new Date();
        return redis.exists("user:" + bean.toUser)
                .doOnNext(num -> rxAssert(num > 0, IMError.TARGET_NOT_EXIST))
                .map(ignore -> userOnline.get(bean.toUser))
                .flatMap(client -> {
                    if (client != null) {
                        send(client, IMEvent.MSG_SYNC, bean);
                        return Observable.just(true);
                    } else {
                        return redis.rpush("msg:" + bean.toUser, JSON.toJSONString(bean))
                                .map(aLong -> aLong > 0);
                    }
                });
    }

    public Observable<List<String> > getFriendList(SocketIOClient client){
        return rxGetUsername(client)
                .flatMap(username -> redis.smembers(username + ":friends"))
                .compose(RxUtils.listSuportNull());
    }

    public Observable<Object> getUserInfo(SocketIOClient client, String username) {
        return redis.hget("user:" + username, "info")
                .compose(RxUtils.suportNull(IMError.TARGET_NOT_EXIST));
    }

    public Observable<Boolean> addFriend(SocketIOClient client, MessageBean bean) {
        String username = client.<String>get("username");
        return redis.exists("user:" + bean.toUser)
                .doOnNext(num -> rxAssert(num > 0, IMError.INVALI_REQUEST))
                .flatMap(aLong -> redis.sadd(username + ":addFriend", bean.toUser))
                .map(aLong1 -> aLong1 > 0)
                .doOnNext(aBoolean -> {
                    if (aBoolean) {
                        sendMessage(bean);
                    }
                });
    }

    public Observable<Boolean> addGroup(SocketIOClient client, GroupBean bean) {

        return redis.exists("group:" + bean.groupName)
                .doOnNext(isExist -> rxAssert(isExist <= 0, IMError.GROUP_NAME_ALREADY_EXIST))
                .flatMap(ignore -> rxGetUsername(client))
                .map(username -> {
                    Group group = new Group(bean);
                    group.owner = username;
                    return group;
                })
                .flatMap(group -> redis.hmset("group:" + group.groupName, group.getMap()))
                .map("OK"::equalsIgnoreCase);
    }

    public Observable<Boolean> replyOfAddFriend(SocketIOClient client, MessageBean bean) {
        String username = client.<String>get("username");

        return redis.srem(bean.toUser + ":addFriend", username)
                .compose(RxUtils.suportNull(IMError.INVALI_REQUEST))
                .doOnNext(num -> rxAssert(num > 0, IMError.INVALI_REQUEST))
                .flatMap(aLong1 -> {
                    if ("YES".equalsIgnoreCase(bean.context)) {
                        return Observable.zip(
                                redis.sadd(username + ":friends", bean.toUser),
                                redis.sadd(bean.toUser + ":friends", username),
                                (aLong, aLong2) -> true
                        );
                    } else return Observable.just(false);
                });
    }

    public Observable<Boolean> joinGroup(SocketIOClient client, MessageBean bean) {
        return redis.exists("group:" + bean.fromGroup)
                .doOnNext(num -> rxAssert(num > 0, IMError.TARGET_NOT_EXIST))
                .flatMap(aLong -> rxGetUsername(client))
                .flatMap(username -> redis.sadd(username + ":joinGroup", bean.fromGroup))
                .map(aLong1 -> aLong1 > 0)
                .doOnNext(aBoolean -> {
                    if (aBoolean) {
                        redis.hget("group:" + bean.fromGroup, "owner")
                                .subscribe(s -> {
                                    bean.toUser = s;
                                    bean.fromUser = client.<String>get("username");
                                    if (bean.fromUser != null)
                                        sendMessage(bean);
                                });
                    }
                });
    }

    public Observable<Boolean> replyOfJoinGroup(SocketIOClient client, MessageBean bean) {
        return Observable
                .zip(
                        redis.hget("group:" + bean.fromGroup, "owner"),
                        rxGetUsername(client),
                        (owner, myName) -> {
                            //验证是否群主
                            rxAssert(StringUtils.equals(owner, myName), IMError.INVALI_REQUEST);
                            return null;
                        }
                )
                .flatMap(ignore -> redis.srem(bean.toUser + ":joinGroup", bean.fromGroup))
                .compose(RxUtils.suportNull(IMError.INVALI_REQUEST))
                //判断是否是为有效的入群回复
                .doOnNext(num -> rxAssert(num > 0, IMError.INVALI_REQUEST))
                .flatMap(ignore -> {
                    if ("YES".equalsIgnoreCase(bean.context)) {
                        return Observable.zip(
                                redis.sadd(bean.toUser + ":myGroups", bean.fromGroup),
                                redis.sadd(bean.fromGroup + ":members", bean.toUser),
                                (r1, r2) -> r1 > 0 && r2 > 0
                        );
                    } else
                        return Observable.just(false);
                })
                .doOnNext(aBoolean -> sendMessage(bean));
    }

    public Observable<Boolean> removeFriend(SocketIOClient client, String username) {
        return rxGetUsername(client)
                .flatMap(myName -> redis.srem(myName + "friends", username))
                .doOnNext(num -> rxAssert(num > 0, IMError.INVALI_REQUEST))
                .map(aLong1 -> aLong1 > 0);
    }

    /**
     * 获取登录状态的用户的用户名
     */
    private Observable<String> rxGetUsername(SocketIOClient client) {
        return Observable
                .just(client.<String>get("username"))
                .doOnNext(username -> rxAssert(
                        username == null || !userOnline.containsKey(username),
                        IMError.UNLOGIN))
                .map(String::valueOf);
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
                                        redis.srem("tmpMsg", JSON.toJSONString(bean)).subscribe();
                                        subscriber.onNext(true);
                                        subscriber.onCompleted();
                                    }

                                    @Override
                                    public void onTimeout() {
                                        super.onTimeout();
                                        subscriber.onError(new TimeoutException());
                                    }
                                })
                )
                .retry((integer, throwable) -> throwable instanceof TimeoutException && integer < 3)
                .subscribe();
    }

    private boolean msgQueueStarted = false;

    public void startMsgQueue() {
        if (msgQueueStarted) return;
        Observable.interval(100, TimeUnit.MILLISECONDS)
                .flatMap(aLong -> Observable.zip(
                        redis.lrange("msgQueue", 0, 1000)),
                        redis.ltrim("msgQueue", 1001, -1),
                        () -> {}
                        )

                .map(s -> JSON.<MessageBean>parseObject(s, MessageBean.class))
//                .map(messageBean -> messageBean)
                .subscribe((bean) -> {
                    sendMessage(bean);
                }, throwable -> restartMsgQueue());
    }

    private void restartMsgQueue() {
        msgQueueStarted = false;
        startMsgQueue();
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
        Stream.iterate(1, integer -> integer + 1)
                .limit(1000000)
                .map(String::valueOf)
                .collect(Collectors.toMap(s -> s, s -> s));
        System.out.printf("cost: %f s", (System.currentTimeMillis() - start) / 1000.0);
        Thread.currentThread().join();
    }
}
