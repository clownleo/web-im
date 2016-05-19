package me.smilence.commons.utils;

import com.google.common.collect.Lists;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.api.rx.RedisReactiveCommands;
import rx.Observable;
import rx.Subscriber;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by leo on 16-5-9.
 */
public class RxUtils {

    private Map<String, String> cache = new HashMap<>();

    private static RedisReactiveCommands<String, String> redis = RedisClient
            .create("redis://localhost")
            .connect()
            .reactive();


    static public <T> Observable.Transformer<T, List<T>> listSupport() {
        return tObservable -> tObservable
                .collect(Lists::<T>newLinkedList, List::add);
    }

    static public <T> Observable.Transformer<T, T> supportNull() {
        return supportNull(null);
    }

    static public <T> Observable.Transformer<T, T> supportNull(Throwable ex) {
        return tObservable ->
                Observable.create(subscriber -> {
                    AtomicBoolean noData = new AtomicBoolean(true);
                    tObservable.subscribe(new Subscriber<T>() {
                        @Override
                        public void onCompleted() {
                            if (noData.get()) {
                                if (ex != null)
                                    subscriber.onError(ex);
                                else
                                    subscriber.onNext(null);
                                subscriber.onCompleted();
                            } else
                                subscriber.onCompleted();
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            subscriber.onError(throwable);
                        }

                        @Override
                        public void onNext(T t) {
                            noData.lazySet(false);
                            subscriber.onNext(t);
                        }
                    });
                });
    }

    /**
     * 判断flag条件，不满足的情况下将RuntimeException抛出
     */
    static public void rxAssert(boolean flag, RuntimeException throwable){
        if(!flag)
            throw throwable;
    }
}
