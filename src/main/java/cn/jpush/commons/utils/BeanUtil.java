package cn.jpush.commons.utils;


import cn.jpush.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.lambdaworks.redis.RedisClient;
import org.apache.commons.beanutils.BeanUtils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Created by leo on 16-5-4.
 */
public class BeanUtil {
    private static ObjectMapper objm = new ObjectMapper();

    public static <T> T map2Bean(Map<String, String> map, Class<T> class1) {
        T bean = null;
        try {
            bean = class1.newInstance();
            BeanUtils.populate(bean, map);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return bean;
    }

    public static <T> Map<String, String> bean2map(T bean) {
        try {
            return objm.readValue(objm.writeValueAsString(bean), Map.class);
        } catch (IOException e) {
            return null;
        }
    }

    public static void main(String[] args) throws IOException {
        User u = new User();
        System.out.println(objm.readValue("{\"info\":{\"nickname\":\"leo\"},\"username\":\"lin\",\"publicKey\":null,\"keySign\":null,\"keyEncrypted\":null}", User.class).getMap());
    }
}
