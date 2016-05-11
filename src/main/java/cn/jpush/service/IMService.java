package cn.jpush.service;

import cn.jpush.IMError;
import cn.jpush.IMEvent;
import cn.jpush.dao.Dao;
import cn.jpush.entity.User;
import cn.jpush.eventbean.*;
import com.alibaba.fastjson.JSON;
import com.corundumstudio.socketio.AckCallback;
import com.corundumstudio.socketio.SocketIOClient;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.api.rx.RedisReactiveCommands;
import org.apache.commons.codec.digest.DigestUtils;
import rx.Observable;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

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
                    if (ex)
                        return redis.hmset("user:" + bean.username, new User(bean).getMap());
                    else
                        throw IMError.USERNAME_ALREADY_EXIST;
                })
                .map("OK"::equalsIgnoreCase);

    }

    public Observable<Boolean> auth(SocketIOClient client, LoginBean bean) {
        return Observable
                .zip(
                        redis.hget("user:" + bean.username, "keySign"),
                        redis.get("stamp:" + bean.username),
                        (s1, s2) -> s1 + ":" + s2
                )
                .compose(Dao.suportNull(IMError.USERNAME_NOT_EXIST))
                .doOnNext(System.out::println)
                .map(DigestUtils::sha256Hex)
                .map((signature) -> {
                    if (bean.signature.equals(signature))
                        return true;
                    else throw IMError.AUTH_FAIL;
                }).doOnNext(success -> {
                    if (success && client != null) {
                        userOnline.put(bean.username, client);
                        client.set("username", bean.username);
                    }
                });
    }

    public Observable<Boolean> logout(SocketIOClient client) {
        return Observable.just(
                client.<String>get("username") != null &&
                        userOnline.remove(client.<String>get("username")) != null
        );
    }

    public Observable<Boolean> setKeyBak(SocketIOClient client, KeyBckBean bean) {
        LoginBean loginBean = new LoginBean();
        loginBean.username = bean.username;
        loginBean.signature = bean.signature;
        return auth(null, loginBean)
                .flatMap(success ->
                                redis.hset("user:" + bean.username, "keyEncryptedBck", bean.keyEncryptedBck)
                );
    }

    public Observable<Boolean> resetKeyEncrypted(SocketIOClient client, ResetKeyEncBean bean) {
        LoginBean loginBean = new LoginBean();
        loginBean.username = bean.username;
        loginBean.signature = bean.signature;
        return auth(null, loginBean)
                .flatMap(success ->
                                redis.hset("user:" + bean.username, "keyEncrypted", bean.keyEncrypted)
                );
    }

    public Observable<String> getKeyEncryptedBck(SocketIOClient client, String username) {
        return redis.hget("user:" + username, "keyEncryptedBck");
    }

    public Observable<String> getKeyEncrypted(SocketIOClient client, String username) {
        return redis.hget("user:" + username, "keyEncrypted")
                .compose(Dao.suportNull(IMError.USERNAME_NOT_EXIST));
    }

    public Observable<Boolean> updateUserInfo(SocketIOClient client, UpdateUserInfoBean bean) {
        if (client.<String>get("username") != null && userOnline.containsKey(client.<String>get("username")))
            return Observable.create(subscriber -> subscriber.onError(IMError.UNLOGIN));
        return redis.hset("user:" + client.<String>get("username"), "info", JSON.toJSONString(bean.info));
    }

    public Observable<Boolean> sendMessage(SocketIOClient client, MessageBean bean) {
        if (client.<String>get("username") != null && userOnline.containsKey(client.<String>get("username")))
            return Observable.create(subscriber -> subscriber.onError(IMError.UNLOGIN));
        bean.fromUser = client.<String>get("username");
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
                .compose(Dao.suportNull(IMError.TARGET_NOT_EXIST));
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

    public Observable<Boolean> replyOfAddFriend(SocketIOClient client, MessageBean bean) {
        String username = client.<String>get("username");

        return redis.srem(bean.toUser + ":addFriend", username)
                .compose(Dao.suportNull(IMError.INVALI_REQUEST))
                .doOnNext(aLong1 -> {
                    if(aLong1 <= 0)
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

    public Observable<Boolean> addGroup(SocketIOClient client, MessageBean bean) {
        String username = client.<String>get("username");
        return redis.exists("group:" + bean.fromGroup)
                .flatMap(aLong -> {
                    if (aLong <= 0)
                        throw IMError.TARGET_NOT_EXIST;
                    return redis.sadd(username + ":addGroup", bean.fromGroup);
                })
                .map(aLong1 -> aLong1 > 0)
                .doOnNext(aBoolean -> {
                    if (aBoolean) {
                        redis.hget("group:" + bean.fromGroup, "owner")
                                .subscribe(s -> {
                                    bean.toUser = s;
                                    sendMessage(client, bean);
                                });
                    }
                });
    }

    public Observable<Boolean> replyOfAddGroup(SocketIOClient client, MessageBean bean) {
        String username = client.<String>get("username");

        return redis.srem(bean.toUser + ":addGroup", bean.fromGroup)
                .compose(Dao.suportNull(IMError.INVALI_REQUEST))
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
                }).doOnNext(aBoolean -> {
                    sendMessage(client, bean);
                });
    }

    public Observable<Boolean> removeFriend(SocketIOClient client, String username) {
        if(client.<String>get("username") == null) {
            return Observable.create(subscriber -> subscriber.onError(IMError.UNLOGIN));
        }
        return redis.srem(client.<String>get("username") + "friends", username)
                .doOnNext(aLong -> {
                    if(aLong == 0)
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
                                        redis.zrem("tmpMsg", JSON.toJSONString(bean));
                                        subscriber.onNext(true);
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

    public static void main(String[] args) throws InterruptedException {
        Observable.create(subscriber -> {
            System.out.println(123);
            subscriber.onError(new NullPointerException());
        }).retry(3).subscribe();
        Thread.currentThread().join();
    }
}
