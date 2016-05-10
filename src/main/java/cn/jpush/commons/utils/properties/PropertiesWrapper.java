package cn.jpush.commons.utils.properties;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 以Key-Value形式保存的配置数据
 * Create by linzh on 16/3/11.
 */
public class PropertiesWrapper {
    private static Map<Class, Converter> converters = new HashMap<>();
    private Properties prop;

    protected PropertiesWrapper(Properties prop) {
        this.prop = prop;
    }

    /**
     * 获取指定key的配置
     *
     * @param key key
     * @return key对应的字符串类型配置
     */
    public String get(String key) {
        return prop.getProperty(key).trim();
    }

    /**
     * 获取指定key的配置
     *
     * @param key   key
     * @param clazz 配置类型
     * @return key对应的配置，封装为clazz类型
     */
    public <T> T get( String key, Class<T> clazz) {
        if(!prop.containsKey(key)) {
            throw new RuntimeException(String.format("properties not contains key %s", key));
        }
        try {
            //通过逻辑保证正确性
            @SuppressWarnings("unchecked")
            Converter<T> converter = converters.get(clazz);
            if (null == converter) {
                throw new RuntimeException(String.format("converter of string to %s not exist", clazz.getSimpleName()));
            }
            return converter.convert(get(key));
        } catch (NullPointerException ex) {
            throw new RuntimeException(String.format("value of %s not found", key));
        }
    }

    /**
     * 获取指定key的配置
     *
     * @param key key
     * @return key对应的int类型配置
     */
    public Integer getInt(String key) {
        return Integer.valueOf(prop.getProperty(key));
    }

    /**
     * 获取指定key的配置
     *
     * @param key key
     * @return key对应的long类型配置
     */
    public Long getLong(String key) {
        return Long.valueOf(prop.getProperty(key));
    }

    /**
     * 获取指定key的配置
     *
     * @param key key
     * @return key对应的double类型配置
     */
    public Double getDouble(String key) {
        return Double.valueOf(prop.getProperty(key));
    }

    /**
     * 获取指定key的配置
     *
     * @param key key
     * @return key对应的boolean类型配置
     */
    public Boolean getBool(String key) {
        String value = prop.getProperty(key);
        return "TRUE".equals(value) || "true".equals(value) || "1".equals(value);
    }

    /**
     * 获取指定key的配置
     *
     * @param key key
     * @return key对应的float类型配置
     */
    public Float getFloat(String key) {
        return Float.valueOf(prop.getProperty(key));
    }

    /**
     * 获取指定key的配置
     * 逗号作为元素分隔符
     *
     * @param key key
     * @return key对应的数组类型配置
     */
    public ArrayList<String> getArray(String key) {
        if(!prop.containsKey(key)) {
            throw new RuntimeException(String.format("properties not contains key %s", key));
        }
        String src = prop.getProperty(key);
        if (null == src)
            return null;
        String[] from = src.split(",");
        ArrayList<String> to = new ArrayList<>(from.length);
        for (String item : from) {
            to.add(item.trim());
        }
        return to;
    }

    /**
     * 获取指定key的配置
     * 逗号作为元素分隔符
     *
     * @param key key
     * @return key对应的clazz类型配置
     */
    public <T> ArrayList<T> getArray(String key, Class<T> clazz) {
        ArrayList<String> src = getArray(key);

        //通过逻辑保证正确性
        @SuppressWarnings("unchecked")
        Converter<T> converter = converters.get(clazz);
        if (null == converter) {
            throw new RuntimeException(String.format("converter of string to %s not exist", clazz.getSimpleName()));
        }
        if (null == src) {
            throw new RuntimeException(String.format("property of key:%s not exist", key) );
        }

        ArrayList<T> result = new ArrayList<>(src.size());
        if(src.size() == 1 && StringUtils.isEmpty(src.get(0)))
            return result;

        try {
            for (String item : src) {
                result.add(converter.convert(item));
            }
            return result;
        } catch (Exception e){
            throw new RuntimeException("convert fail, cause by format error probablely", e);
        }
    }

    public Map<String, String> getMap() {
        Map<String, String> map = new HashMap<>();
        for(Map.Entry entry:this.prop.entrySet()) {
            map.put(
                entry.getKey().toString(),
                entry.getValue().toString()
            );
        }
        return map;
    }

    /**
     * 获取配置后，将字符串转换为指定类型的转换器
     * @param <T> 转换的目标类型
     */
    public interface Converter<T> {
        T convert(String src);
    }

    /**
     * 注册默认转换器，以支持基本类型
     */
    static {
        Converter integerConverter = new Converter<Integer>() {
            @Override
            public Integer convert(String src) {
                return Integer.valueOf(src);
            }
        };
        converters.put(int.class, integerConverter);
        converters.put(Integer.class, integerConverter);
        Converter longConverter = new Converter<Long>() {
            @Override
            public Long convert(String src) {
                return Long.valueOf(src);
            }
        };
        converters.put(long.class, longConverter);
        converters.put(Long.class, longConverter);
        Converter floatConverter = new Converter<Float>() {
            @Override
            public Float convert(String src) {
                return Float.valueOf(src);
            }
        };
        converters.put(float.class, floatConverter);
        converters.put(Float.class, floatConverter);
        Converter doubleConverter = new Converter<Double>() {
            @Override
            public Double convert(String src) {
                return Double.valueOf(src);
            }
        };
        converters.put(double.class, doubleConverter);
        converters.put(Double.class, doubleConverter);
        Converter booleanConverter = new Converter<Boolean>() {
            @Override
            public Boolean convert(String src) {
                return "TRUE".equalsIgnoreCase(src) || "1".equals(src);
            }
        };
        converters.put(boolean.class, booleanConverter);
        converters.put(Boolean.class, booleanConverter);
        Converter shortConverter = new Converter<Short>() {
            @Override
            public Short convert(String src) {
                return Short.valueOf(src);
            }
        };
        converters.put(short.class, shortConverter);
        converters.put(Short.class, shortConverter);
        converters.put(String.class, new Converter() {
            @Override
            public Object convert(String src) {
                return src;
            }
        });
    }

    /**
     * 添加一个String到clazz类型的转换器，以支持获取指定类型的配置
     * @param clazz     目标转换类型
     * @param converter 自定的String类型到Clazz类型的转换器，实现Converter接口
     * @param <T>       转换结果
     */
    public static <T> void registConverter(Class<T> clazz, Converter<T> converter) {
        converters.put(clazz, converter);
    }

    public static void main(String[] args) {

    }
}