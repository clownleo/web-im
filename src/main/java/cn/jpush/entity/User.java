package cn.jpush.entity;

import cn.jpush.eventbean.RegisterBean;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 *
 * Created by leo on 16-5-9.
 */
public class User {
    public Object info;
    public String username;
    public String publicKey;
    public String keySign;
    public String keyEncrypted;

    public User(){}

    public User(RegisterBean bean){
        this.username=bean.username;
        this.keyEncrypted=bean.keyEncrypted;
        this.keySign=bean.keySign;
        this.publicKey=bean.publicKey;
        this.info=bean.info;
    }

    public Map<String, String> getMap(){
        Map<String, String> result = Maps.<String, String>newHashMap();
        result.put("username", username);
        result.put("publicKey", publicKey);
        result.put("keySign", keySign);
        result.put("keyEncrypted", keyEncrypted);
        result.put("info", JSON.toJSONString(info));
        return result;
    }
    
    public static User getUser(Map<String, String> result) {
        User u = new User();
        u.username = result.get("username");
        u.publicKey = result.get("publicKey");
        u.keySign = result.get("keySign");
        u.keyEncrypted = result.get("keyEncrypted");
        u.info = JSON.parseObject(result.get("info"));
        return u;
    }
}
