package me.smilence.commons.utils;

import com.google.common.collect.Lists;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.api.rx.RedisReactiveCommands;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import rx.Observable;
import rx.Subscriber;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Created by leo on 16-5-9.
 */
public class RxUtils {

    private Map<String, String> cache = new HashMap<>();

    private static RedisReactiveCommands<String, String> redis = RedisClient
            .create("redis://localhost")
            .connect()
            .reactive();


    static public <T> Observable.Transformer<T, List<T>> listSuportNull() {
        return tObservable -> tObservable
                .collect(Lists::<T>newLinkedList, List::add);
    }

    static public <T> Observable.Transformer<T, T> suportNull() {
        return suportNull(null);
    }

    static public <T> Observable.Transformer<T, T> suportNull(Throwable ex) {
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
                            }
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
