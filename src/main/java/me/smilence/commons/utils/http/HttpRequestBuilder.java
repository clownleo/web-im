package me.smilence.commons.utils.http;

import com.alibaba.fastjson.JSON;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;

/**
 * post get put delete等方法的建造者
 * 链式调用的构造风格
 * 内容包括http request method、 params、 headers等
 * 支持RESTful，指定body为json的方式为：添加JSON_CONTENT_TYPE_WITH_UTF_8的header
 * Created by leolin on 16/1/25.
 */
public class HttpRequestBuilder {
    private String url = null;
    private Object jsonBody = null;
    private List<NameValuePair> params = new LinkedList<>();
    private RequestType method = null;
    private Set<Header> headers = new HashSet<>();
    private String encode;
    private Map<String, File> files = new HashMap<>();
    public static Header JSON_CONTENT_TYPE_WITH_UTF_8 = new UniqueKeyHeader("Content-Type", "application/json; charset=utf-8");
    public static Header FORM_CONTENT_TYPE_WITH_UTF_8 = new UniqueKeyHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
    public static Header MULTIPART_CONTENT_TYPE = new UniqueKeyHeader("Content-Type", "multipart/form-data");
    public static Header ACCEPT_JSON = new UniqueKeyHeader("Accept", "application/json");
    //默认contentType，作为内部解析使用，不会直接出现在http request header中
    private String contentType = "application/x-www-form-urlencoded";

    public enum RequestType {
        POST, GET, PUT, DELETE
    }

    private HttpRequestBuilder() {}

    public static HttpRequestBuilder getBuilder(RequestType method) {
        HttpRequestBuilder builder = new HttpRequestBuilder();
        builder.method = method;
        //set suggest default encode
        switch (method) {
            case POST:
            case PUT:
                builder.encode = "utf-8";
                break;

            case GET:
            case DELETE:
                builder.encode = "gbk";
                break;
        }
        return builder;
    }

    public HttpRequestBuilder addParam(List<NameValuePair> params) {
        for (NameValuePair item : params) {
            addParam(item);
        }
        return this;
    }

    public HttpRequestBuilder addParam(NameValuePair param) {
        this.params.add(param);
        return this;
    }

    public HttpRequestBuilder addParam(String name, Object value) {
        this.addParam(new BasicNameValuePair(name, String.valueOf(value)));
        return this;
    }

    public HttpRequestBuilder addHeader(List<Header> headers) {
        for (Header header : headers) {
            addHeader(header);
        }
        return this;
    }

    public HttpRequestBuilder addHeader(Header header) {
        if ("Content-Type".equals(header.getName())) {
            contentType = header.getValue();
        }

        if(!UniqueKeyHeader.class.isInstance(header)) {
            header = new UniqueKeyHeader(header);
        }

        //重复的key以覆盖的形式处理
        this.headers.remove(header);
        this.headers.add(header);
        return this;
    }

    public HttpRequestBuilder addHeader(String key, Object value) {
        this.addHeader(new UniqueKeyHeader(key, String.valueOf(value)));
        return this;
    }

    public HttpRequestBuilder basicAuth(String username, String password) {
        addHeader(
            new UniqueKeyHeader(
                HttpHeaders.AUTHORIZATION,
                "Basic " + Base64.encodeBase64String(
                    (username + ":" + password).getBytes()
                )
            )
        );
        return this;
    }

    /**
     * 上传文件，暂时仅支持post/put方法（get delete从协议上就不支持）
     * 注：上传文件必须添加MULTIPART_CONTENT_TYPE头
     * @param fieldName 字段名
     * @param file 要上传的文件
     */
    public HttpRequestBuilder addFile(String fieldName, File file) {
        files.put(fieldName, file);
        return this;
    }

    public HttpRequestBuilder appkey(String appkey) {
        addHeader("X-App-Key", appkey);
        return this;
    }

    public HttpRequestBase build() {
        if (this.url == null)
            throw new RuntimeException("property 'url' must be not null");

        HttpRequestBase baseRequest = null;
        HttpEntityEnclosingRequestBase enclosingRequest = null;
        try {
            switch (this.method) {
                case POST:
                    enclosingRequest = new HttpPost(url);
                    break;
                case PUT:
                    enclosingRequest = new HttpPut(url);
                    break;

                case GET:
                    baseRequest = new HttpGet(urlJoinWithParams(url, params, encode));
                    break;
                case DELETE:
                    baseRequest = new HttpDelete(urlJoinWithParams(url, params, encode));
                    break;

            }

            if (enclosingRequest != null) {
                enclosingRequest.setEntity(getEntity());
                for (Header header : headers) {
                    enclosingRequest.addHeader(header);
                }
                return enclosingRequest;
            } else if (baseRequest != null) {
                for (Header header : headers) {
                    baseRequest.addHeader(header);
                }
                return baseRequest;
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }


    //get delete等http请求方式的传参手段
    private static String urlJoinWithParams(String url, List<NameValuePair> params, String encode) {
        if (0 == params.size()) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + URLEncodedUtils.format(params, encode);
    }

    private HttpEntity getEntity() {
        if (contentType.contains("application/json")) {
            String str;
            if(jsonBody != null && params.size() != 0) {
                throw new RuntimeException("jsonBody and params can't exist at the same time");
            }
             if(jsonBody == null) {
                Map<String, String> data = new HashMap<>();
                for (NameValuePair param : params) {
                    data.put(param.getName(), param.getValue());
                }
                str = JSON.toJSONString(data);
            } else {
                 if(String.class.isInstance(jsonBody) ) {
                     str = (String) jsonBody;
                 } else {
                     str = JSON.toJSONString(jsonBody);
                 }
            }

            return new StringEntity(str, Charset.forName(encode));

        } else if (contentType.contains("multipart/form-data")) {
            ContentType type = ContentType.create(HTTP.PLAIN_TEXT_TYPE, encode);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setCharset(Charset.forName(encode));//设置请求的编码格式
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);//设置浏览器兼容模式
            for (Map.Entry<String, File> file : files.entrySet()) {
                builder.addPart(file.getKey(), new FileBody(file.getValue()));
            }
            for (NameValuePair param : params) {
                    builder.addTextBody(param.getName(), param.getValue(), type);
            }
            HttpEntity result = builder.build();
            addHeader(result.getContentType());
            return result;

        } else {
            return new UrlEncodedFormEntity(params, Charset.forName(encode));
        }
    }

    public HttpRequestBuilder setEncode(String encode) {
        this.encode = encode;
        return this;
    }

    public HttpRequestBuilder setUrl(String url) {
        this.url = url;
        return this;
    }

    public HttpRequestBuilder setMethod(RequestType method) {
        this.method = method;
        return this;
    }

    /**
     * 在content-type="application/json"的情况
     * jsonBody或者param只能出现一个
     * 如果同时存在，则出现runtime exception
     * @param jsonBody jsonBody
     */
    public HttpRequestBuilder setJsonBody(Object jsonBody) {
        this.jsonBody = jsonBody;
        return this;
    }


    private static class UniqueKeyHeader extends BasicHeader {

        public UniqueKeyHeader(String name, String value) {
            super(name, value);
        }

        public UniqueKeyHeader(Header header) {
            this(header.getName(), header.getValue());
        }

        @Override
        public int hashCode() {
            return this.getName().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if(!UniqueKeyHeader.class.isInstance(obj))
                return false;
            Header header = (Header) obj;
            return Objects.equals(this.getName(), header.getName());
        }
    }

}
