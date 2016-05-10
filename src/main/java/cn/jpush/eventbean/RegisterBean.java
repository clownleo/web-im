package cn.jpush.eventbean;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * for register
 * Created by leo on 16-5-8.
 */
public class RegisterBean extends EventBean {
    public Object info;
    public String username;
    @JsonProperty("pub_key")
    public String publicKey;
    @JsonProperty("key_sign")
    public String keySign;
    @JsonProperty("key_encrypted")
    public String keyEncrypted;

    @Override
    public boolean validate() {
        return super.validate() &&
                StringUtils.isNoneEmpty(
                        username,
                        publicKey,
                        keySign,
                        keyEncrypted
                );
    }

}
