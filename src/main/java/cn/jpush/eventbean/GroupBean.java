package cn.jpush.eventbean;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

/**
 * for register
 * Created by leo on 16-5-8.
 */
public class GroupBean extends EventBean {
    public String groupName;
    public Object info;
    @Override
    public boolean validate() {
        return super.validate() &&
                StringUtils.isNoneEmpty(
                        this.groupName
                );
    }

}
