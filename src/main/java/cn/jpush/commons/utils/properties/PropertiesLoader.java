package cn.jpush.commons.utils.properties;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 配置文件Loader
 * 主要有5种加载方式
 *      1、通过要注入配置的类T的@Config注解加载文件名，类内字段的@Key注解加载配置字段名，注入到一个对应类T的实例中 -- 生成T类型配置对象
 *      2、通过要注入配置的类T的@Config注解加载文件名，类内字段的@Key注解加载配置字段名，注入到一个对应类中 -- 注入到T类的静态字段
 *      3、指定文件名，通过配置类T内字段的@Key注解加载配置字段名，注入到一个对应类T的实例中 -- 生成T类型配置对象
 *      4、指定文件名，通过配置类T内字段的@Key注解加载配置字段名，注入到一个对应类T中 -- 注入到T类的静态字段
 *      5、通过指定文件名读取配置文件，封装为一个PropertiesWrapper对象。
 *
 *  支持如下特性
 *      1、支持基于文件名的缓存操作
 *      2、支持自定义字段类型（通过注册字符串到目标类型的转换器）
 *
 *  注意：做静态配置读取时，如果@Key注解作用非static字段，将会报错。
 * Create by linzh on 16/3/11.
 */
public class PropertiesLoader {
    private static Map<String, PropertiesWrapper> propertiesWrapperMap = new HashMap<>();

    /**
     * 加载一个配置文件，自动注入到new出来的T类型该对象并返回。
     * 注入字段规则如下：
     * 配置文件从T类的@Config注解的value中获取
     * T类有@key注解的字段，取注解的value(若无值则取字段名)的同名配置项，注入到字段中
     * @param cls 加载的对象类型
     */
    public static <T> T load(Class<T> cls) {
        // 读取配置文件
        Config config = (Config) cls.getAnnotation(Config.class);
        if (config == null || StringUtils.isEmpty(config.value())) {
            throw new RuntimeException(String.format("Can not find %s's properties", cls.getSimpleName()));
        }

        return load(cls, config.value());
    }

    /**
     * 加载一个配置文件file，自动注入到T类。
     * 注入字段规则如下：
     * 配置文件从T类的@Config注解的value中获取
     * T类有@key注解的字段，取注解的value(若无值则取字段名)的同名配置项，注入到静态字段中
     * @param cls 加载的对象类型
     */
    public static <T> void staticLoad(Class<T> cls) {
        // 读取配置文件
        Config config = (Config) cls.getAnnotation(Config.class);
        if (config == null || StringUtils.isEmpty(config.value())) {
            throw new RuntimeException(String.format("Can not find %s's properties", cls.getSimpleName()));
        }

        load(cls, config.value(), null);
    }

    /**
     * 加载一个配置文件file，自动注入到T类。
     * 注入字段规则如下：
     * 配置文件从T类的@Config注解的value中获取
     * T类有@key注解的字段，取注解的value(若无值则取字段名)的同名配置项，注入到静态字段中
     * @param cls 加载的对象类型
     */
    public static <T> void staticLoad(Class<T> cls, String fileName) {
        load(cls, fileName, null);
    }

    public static <T> T load(Class<T> cls, String fileName) {
        try {
            return load(cls, fileName, cls.newInstance());
        } catch (InstantiationException | IllegalAccessException ignored) {}
        return null;
    }

    /**
     * 加载一个配置文件file，自动注入字段到obj对象，而后返回obj。
     * obj为空代表要注入配置到T类，而不是对象
     * 注入字段规则如下：
     * T类有@key注解的字段，取注解的value(若若无值则取字段名)的同名配置项，注入到T的实例obj中
     * @param cls 加载的对象类型
     * @param fileName 配置文件
     * @param obj 要注入的对象
     * @return 返回注入配置后的对象
     */
    private static <T> T load(Class<T> cls, String fileName, T obj) {
        PropertiesWrapper prop = load(fileName);

        // 获取需要注入配置的字段
        Map<Field, Key> keyFields = new HashMap<Field, Key>();
        Field[] fields = cls.getFields();
        for (Field field : fields) {
            Key key = field.getAnnotation(Key.class);
            //不是配置字段 或 要做静态注入(注入到类而不是对象),但是字段并非类字段(静态字段)，则跳过
            if (key == null)
                continue;
            if (obj == null &&  !Modifier.isStatic(field.getModifiers()) ) {
                throw new RuntimeException(
                    String.format("non-static field('%s') is not support in static load", field.getName())
                );
            }

            keyFields.put(field, field.getAnnotation(Key.class));
        }

        // 注入配置
        for (Field field : keyFields.keySet()) {
            String key = keyFields.get(field).value();
            if(StringUtils.isEmpty(key) ) {
                key = field.getName();
            }
            Class type = field.getType();
            try {
                field.setAccessible(true);
                field.set(obj, prop.get(key, type));
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Can not access field, " + e.getMessage());
            }

        }
        return obj;
    }

    /**
     * 加载一个配置文件到PropertiesWrapper对象
     * @param filename 文件名
     * @return PropertiesWrapper对象
     */
    public static synchronized PropertiesWrapper load(String filename) {
        if (StringUtils.isEmpty(filename)) {
            throw new IllegalArgumentException("Properties filename can't not be null");
        }

        if (propertiesWrapperMap.containsKey(filename)) {
            return propertiesWrapperMap.get(filename);
        }

        Properties prop = new Properties();
        try {
            prop.load(PropertiesLoader.class.getClassLoader().getResourceAsStream(filename));
        } catch (IOException e) {
            throw new RuntimeException(String.format("Can not load properties from %s", filename));
        }

        PropertiesWrapper wrapper = new PropertiesWrapper(prop);
        propertiesWrapperMap.put(filename, wrapper);
        return wrapper;
    }


    /**
     * 添加一个String到clazz类型的转换器，以支持获取指定类型的配置
     * 仅通过PropertiesLoader调用
     * @param clazz     目标转换类型
     * @param converter 自定的String类型到Clazz类型的转换器，实现Converter接口
     * @param <T>       转换结果
     */
    static <T> void registConverter(Class<T> clazz, PropertiesWrapper.Converter<T> converter){
        PropertiesWrapper.registConverter(clazz, converter);
    }

    private PropertiesLoader() {
    }
}
