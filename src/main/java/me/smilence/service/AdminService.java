package me.smilence.service;

import com.corundumstudio.socketio.SocketIOClient;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.api.rx.RedisReactiveCommands;
import com.lambdaworks.redis.api.sync.RedisCommands;
import me.smilence.IMError;
import me.smilence.commons.utils.AES;
import me.smilence.commons.utils.RxUtils;
import me.smilence.commons.utils.properties.PropertiesLoader;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import rx.Observable;
import sun.security.jca.GetInstance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * administrator service
 * Created by leo on 16-5-8.
 */
public class AdminService {
    private IMService imService = IMService.getInstance();
    private String secrete = PropertiesLoader.load("system.properties").get("admin-secrete");
    private static AdminService instance = new AdminService();

    private AdminService() {
    }

    public static AdminService getInstance() {
        return instance;
    }


    public Observable<String> newStamp(SocketIOClient client) {
        String uuid = String.valueOf(UUID.randomUUID());
        client.set("stamp", uuid);
        return Observable.just(uuid);
    }

    private static Map<String, SocketIOClient> userOnline = new ConcurrentHashMap<>();
    private static RedisReactiveCommands<String, String> rxRedis = RedisClient
            .create("redis://localhost")
            .connect()
            .reactive();
    private static RedisCommands<String, String> redis = RedisClient
            .create("redis://localhost")
            .connect()
            .sync();

    public Observable<Boolean> removeUser(String username) {
        return rxRedis.del("user:" + username)
                .compose(RxUtils.supportNull(IMError.TARGET_NOT_EXIST))
                .doOnNext(aLong -> RxUtils.rxAssert(aLong > 0, IMError.TARGET_NOT_EXIST))
                .doOnCompleted(() -> Observable.merge(
                                rxRedis.smembers(username + ":friends")
                                        //为所有好友删除该用户的关系
                                        .flatMap(friend -> rxRedis.srem(friend + ":friends", username))
                                        .doOnCompleted(() -> rxRedis.del(username + ":friends").subscribe()),

                                rxRedis.smembers(username + ":myGroups")
                                        //为所有群组删除该用户的关系
                                        .flatMap(group -> rxRedis.srem(group + ":members", username))
                                        .doOnCompleted(() -> rxRedis.del(username + ":myGroups").subscribe())

                        ).subscribe()
                )
                .map(ignore -> true)
                .doOnNext(aBoolean -> imService.deleteUser(username));
    }

    public Observable<Boolean> removeGroup(String group) {
        return rxRedis.del("group:" + group)
                .compose(RxUtils.supportNull(IMError.TARGET_NOT_EXIST))
                .doOnNext(aLong -> RxUtils.rxAssert(aLong > 0, IMError.TARGET_NOT_EXIST))
                .doOnCompleted(() ->
                                rxRedis.smembers(group + ":members")
                                        //为所有好友删除该用户的关系
                                        .flatMap(member -> rxRedis.srem(member + ":myGroups", group))
                                        .doOnCompleted(() -> rxRedis.del(group + ":members").subscribe())
                                        .subscribe()
                )
                .map(ignore -> true);
    }

    public Observable<Boolean> suspendUser(String username) {
        return rxRedis.exists("user:" + username)
                .doOnNext(ok -> RxUtils.rxAssert(ok > 0, IMError.TARGET_NOT_EXIST))
                .flatMap(ignore -> rxRedis.hset("user:" + username, "isSuspended", "isSuspended"))
                .compose(RxUtils.supportNull(IMError.TARGET_NOT_EXIST))
                .doOnNext(ignore -> imService.suspendUser(username));
    }

    public Observable<Boolean> unSuspendUser(String username) {
        return rxRedis.exists("user:" + username)
                .doOnNext(ok -> RxUtils.rxAssert(ok > 0, IMError.TARGET_NOT_EXIST))
                .flatMap(ignore -> rxRedis.hdel("user:" + username, "isSuspended"))
                .compose(RxUtils.supportNull(IMError.TARGET_NOT_EXIST))
                .doOnNext(aLong -> RxUtils.rxAssert(aLong > 0, IMError.TARGET_NOT_EXIST))
                .map(aLong -> aLong > 0);
    }

    public Observable<Boolean> suspendGroup(String group) {
        return rxRedis.exists("group:" + group)
                .doOnNext(ok -> RxUtils.rxAssert(ok > 0, IMError.TARGET_NOT_EXIST))
                .flatMap(ignore -> rxRedis.hset("group:" + group, "isSuspended", "isSuspended"))
                .compose(RxUtils.supportNull(IMError.TARGET_NOT_EXIST))
                .doOnNext(ignore -> imService.changeGroupSuspended(group));
    }

    public Observable<Boolean> unSuspendGroup(String group) {
        return rxRedis.exists("group:" + group)
                .doOnNext(ok -> RxUtils.rxAssert(ok > 0, IMError.TARGET_NOT_EXIST))
                .flatMap(ignore -> rxRedis.hdel("group:" + group, "isSuspended"))
                .doOnNext(aLong -> RxUtils.rxAssert(aLong > 0, IMError.TARGET_NOT_EXIST))
                .map(aLong -> aLong > 0)
                .doOnNext(ignore -> imService.changeGroupSuspended(group));
    }

    public Boolean validate(SocketIOClient client, String signature) {
        boolean result = client.get("stamp") != null &&
                StringUtils.equals(
                        DigestUtils.sha256Hex(secrete + ":" + client.<String>get("stamp")),
                        signature
                );
        if (result) client.del("stamp");
        return result;
    }
}
