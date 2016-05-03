package cn.jpush.commons.utils.http;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

/**
 * http错误的封装，不包括IO等异常
 * 提示性的message存放在message，非必须
 * status表示http状态码
 * response是附加的http响应对象，便于获取响应体的完整信息。
 * Created by leolin on 16/1/26.
 */
public class HttpException extends Exception implements Result {
    private Integer status;
    private HttpResponse response;
    private String decode;
    private String body;
    private Result result;

    public HttpException(HttpResponse response, String decode){
        super(
            String.format(
                "【HTTP Error code:%d, reason:%s",
                response.getStatusLine().getStatusCode(),
                response.getStatusLine().getReasonPhrase()
            )
        );
        result = new ResultImp(response, decode);
    }

    public HttpException(Result result) {
        super(String.format("【HTTP Error code:%d, reason:%s",
            result.getResponse().getStatusLine().getStatusCode(),
            result.getResponse().getStatusLine().getReasonPhrase()));
        this.result = result;
    }

    public Integer getStatus() {
        return result.getStatus();
    }

    public void setStatus(Integer status) {
        result.setStatus(status);
    }

    public HttpResponse getResponse() {
        return result.getResponse();
    }

    public void setResponse(HttpResponse response) {
        result.setResponse(response);
    }

    /**
     * get response body
     * @return response body
     */
    public String getBody() {
        return result.getBody();
    }

    /**
     * get http header
     * @return http headers
     */
    public Header[] getHeaders() {
        return result.getHeaders();
    }
}
