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
 * administrator service
 * Created by leo on 16-5-8.
 */
public class AdminService {
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
                .map(ignore -> true);
    }

    public Observable<Boolean> removeGroup(String group) {
        return rxRedis.del("group:" + group)
                .compose(RxUtils.supportNull(IMError.TARGET_NOT_EXIST))
                .doOnCompleted(() ->
                        rxRedis.smembers(group + ":members")
                                //为所有好友删除该用户的关系
                                .flatMap(member -> rxRedis.srem(member + ":myGroups", group))
                                .doOnCompleted(() -> rxRedis.del(group + ":members").subscribe())
                                .subscribe()
                )
                .map(ignore -> true);
    }
}
