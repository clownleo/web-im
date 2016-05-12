package me.smilence.commons.utils.http;

import org.apache.http.Header;
import org.apache.http.HttpResponse;

/**
 * Created by leolin on 16/3/16.
 */
public interface Result {
    Integer getStatus();

    void setStatus(Integer status);

    HttpResponse getResponse();

    void setResponse(HttpResponse response);

    String getBody();

    Header[] getHeaders();
}
