package cn.jpush.service;

import cn.jpush.IMError;
import cn.jpush.IMEvent;
import cn.jpush.commons.utils.AES;
import cn.jpush.commons.utils.RxUtils;
import cn.jpush.entity.Group;
import cn.jpush.entity.User;
import cn.jpush.eventbean.*;
import com.alibaba.fastjson.JSON;
import com.corundumstudio.socketio.AckCallback;
import com.corundumstudio.socketio.SocketIOClient;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.api.rx.RedisReactiveCommands;
import rx.Observable;
import rx.Subscriber;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                .map(aLong -> aLong == 1)
                .flatMap(ex -> {
                    if (!ex)
                        return redis.hmset("user:" + bean.username, new User(bean).getMap());
                    else
                        throw IMError.USERNAME_ALREADY_EXIST;
                })
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
                .map((keySign) -> {
                    if (AES.Encrypt(stamp, keySign).equals(bean.signature))
                        return true;
                    else throw IMError.AUTH_FAIL;
                }).doOnNext(success -> {
                    if (success) {
                        client.del("stamp");
                        userOnline.put(bean.username, client);
                        client.set("username", bean.username);
                    }
                });
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

    public Observable<Boolean> sendMessage(MessageBean bean) {
        bean.dateTime = new Date();
        return redis.exists("user:" + bean.toUser)
                .map(aLong -> aLong > 0)
                .flatMap(isExist -> {
                    if (isExist) {
                        SocketIOClient cl = userOnline.get(bean.toUser);
                        if (cl != null) {
                            send(cl, IMEvent.MSG_SYNC, bean);
                            return Observable.just(true);
                        } else {
                            return redis.lpush("msg:" + bean.toUser, JSON.toJSONString(bean))
                                    .map(aLong -> aLong > 0);
                        }
                    } else {
                        redis.zrem("tmpMsg", JSON.toJSONString(bean)).retry().subscribe();
                        throw IMError.TARGET_NOT_EXIST;
                    }
                });
    }

    public Observable<Object> getUserInfo(SocketIOClient client, String username) {
        return redis.hget("user:" + username, "info")
                .compose(RxUtils.suportNull(IMError.TARGET_NOT_EXIST));
    }

    public Observable<Boolean> addFriend(SocketIOClient client, MessageBean bean) {
        String username = client.<String>get("username");
        return redis.exists("user:" + bean.toUser)
                .flatMap(aLong -> {
                    if (aLong <= 0)
                        throw IMError.TARGET_NOT_EXIST;
                    return redis.sadd(username + ":addFriend", bean.toUser);
                })
                .map(aLong1 -> aLong1 > 0);
    }

    public Observable<Boolean> addGroup(SocketIOClient client, GroupBean bean) {
        String username = client.<String>get("username");
        Group group = new Group(bean);
        group.owner = username;
        if (username == null) {
            return Observable.create(subscriber -> subscriber.onError(IMError.UNLOGIN));
        }
        return redis.exists("group:" + bean.groupName)
                .flatMap(aLong -> {
                    if (aLong <= 0)
                        throw IMError.GROUP_NAME_ALREADY_EXIST;
                    return redis.hmset("group:" + group.groupName, group.getMap());
                })
                .map("OK"::equalsIgnoreCase);
    }

    public Observable<Boolean> replyOfAddFriend(SocketIOClient client, MessageBean bean) {
        String username = client.<String>get("username");

        return redis.srem(bean.toUser + ":addFriend", username)
                .compose(RxUtils.suportNull(IMError.INVALI_REQUEST))
                .doOnNext(aLong1 -> {
                    if (aLong1 <= 0)
                        throw IMError.INVALI_REQUEST;
                })
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
        String username = client.<String>get("username");
        return redis.exists("group:" + bean.fromGroup)
                .flatMap(aLong -> {
                    if (aLong <= 0)
                        throw IMError.TARGET_NOT_EXIST;
                    return redis.sadd(username + ":joinGroup", bean.fromGroup);
                })
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

    public Observable<Boolean> replyOfJsonGroup(SocketIOClient client, MessageBean bean) {
        String username = client.<String>get("username");

        return redis.srem(bean.toUser + ":joinGroup", bean.fromGroup)
                .compose(RxUtils.suportNull(IMError.INVALI_REQUEST))
                .doOnNext(aLong3 -> {
                    if (aLong3 <= 0)
                        throw IMError.INVALI_REQUEST;
                })
                .flatMap(aLong1 -> {
                    if ("YES".equalsIgnoreCase(bean.context)) {
                        return Observable.zip(
                                redis.sadd(bean.toUser + ":myGroups", bean.fromGroup),
                                redis.sadd(bean.fromGroup + ":members", bean.toUser),
                                (aLong, aLong2) -> true
                        );
                    } else return Observable.just(false);
                }).doOnNext(aBoolean -> sendMessage(bean));
    }

    public Observable<Boolean> removeFriend(SocketIOClient client, String username) {
        if (client.<String>get("username") == null) {
            return Observable.create(subscriber -> subscriber.onError(IMError.UNLOGIN));
        }
        return redis.srem(client.<String>get("username") + "friends", username)
                .doOnNext(aLong -> {
                    if (aLong == 0)
                        throw IMError.INVALI_REQUEST;
                }).map(aLong1 -> aLong1 > 0);
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
                                        redis.zrem("tmpMsg", JSON.toJSONString(bean)).subscribe();
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
                .flatMap(aLong -> redis.lrange("msgQueue", 0, 1000))
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
