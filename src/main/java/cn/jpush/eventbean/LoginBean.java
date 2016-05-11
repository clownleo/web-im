package cn.jpush.eventbean;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

/**
 * for register
 * Created by leo on 16-5-8.
 */
public class LoginBean extends EventBean {
    public String username;
    @JsonProperty("stamp")
    public String stamp;
    @JsonProperty("signature")
    public String signature;

    @Override
    public boolean validate() {
        return super.validate() &&
                StringUtils.isNoneEmpty(
                        username,
                        signature
                );
    }

}
