package cn.jpush.entity;

import cn.jpush.eventbean.GroupBean;
import cn.jpush.eventbean.RegisterBean;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * Created by leo on 16-5-9.
 */
public class Group {
    public Object info;
    public String groupName;
    public String owner;

    public Group() {
    }

    public Group(GroupBean bean) {
        this.groupName = bean.groupName;
        this.info = bean.info;
    }

    public Map<String, String> getMap() {
        Map<String, String> result = Maps.<String, String>newHashMap();
        result.put("groupName", groupName);
        result.put("owner", owner);
        if (info != null)
            result.put("info", JSON.toJSONString(info));
        return result;
    }

    public static Group getGroup(Map<String, String> result) {
        Group u = new Group();
        u.groupName=result.get("groupName");
        u.owner=result.get("owner");
        u.info = JSON.parseObject(result.get("info"));
        return u;
    }
}
