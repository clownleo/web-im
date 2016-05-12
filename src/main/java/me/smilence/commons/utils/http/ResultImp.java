package me.smilence.commons.utils.http;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

/**
 * http结果的封装
 * 提示性的message存放在message，非必须
 * status表示http状态码
 * response是附加的http响应对象，便于获取响应体的完整信息。
 * Created by leolin on 16/1/26.
 */
public class ResultImp implements Result {
    private Integer status;
    private HttpResponse response;
    private String decode;
    private String body;

    public ResultImp(HttpResponse response, String decode){
        this.status = response.getStatusLine().getStatusCode();
        this.response = response;
        this.decode = decode;
        try {
            HttpEntity entity = this.response.getEntity();
            if(entity == null) {
                body = null;
            } else {
            body = EntityUtils.toString(this.response.getEntity(), this.decode);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer getStatus() {
        return status;
    }

    @Override
    public void setStatus(Integer status) {
        this.status = status;
    }

    @Override
    public HttpResponse getResponse() {
        return response;
    }

    @Override
    public void setResponse(HttpResponse response) {
        this.response = response;
    }

    /**
     * get response body
     * @return response body
     */
    @Override
    public String getBody() {
        return this.body;
    }

    /**
     * get http header
     * @return http headers
     */
    @Override
    public Header[] getHeaders() {
        return response.getAllHeaders();
    }
}
