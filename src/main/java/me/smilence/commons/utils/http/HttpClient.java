package me.smilence.commons.utils.http;

import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

/**
 * 高效的http工具
 * 主要通过HttpRequestBuilder生成请求体，然后交给HttpClient做请求
 * 示例
 *   String url = "http://183.232.42.208:8080" + "/token";
 *   String charset = "utf-8";
 *   HttpClient httpClient = HttpClient.getHttpClient();
 *   HttpRequestBase request = new HttpRequestBuilder(HttpRequestBuilder.RequestType.GET)
 *       .setUrl(url)
 *       .addHeader(HttpRequestBuilder.JSON_CONTENT_TYPE_WITH_UTF_8)
 *       .basicAuth("13178770", "222222")
 *       .build();
 *   String result = httpClient.doRequest(request, "utf-8");
 *  Created by leolin on 16/1/25.
 */
public class HttpClient {
    protected DefaultHttpClient client;

    private HttpClient(){}

    public static HttpClient getHttpClient(){
        HttpClient httpClient = new HttpClient();
        httpClient.client = new DefaultHttpClient();
        return httpClient;
    }

    public static HttpClient getHttpsClient(){
        HttpClient httpClient = new HttpClient();
        httpClient.client = new SSLClient();
        return httpClient;
    }

    /**
     * 获取一个HTTP client
     * 根据URL的协议自动选择HTTP或者HTTPS的client
     * 使用虽然简便，但是对于cookies的操纵容易出现问题。因为HTTP和HTTPS的client各自持有自己的上下文。
     * 尽量避免在需要同时使用两种协议的情况下使用cookies
     */
    public static HttpClient getSimpleClient(){
        return new HttpClient(){
            private DefaultHttpClient
                httpClient = new DefaultHttpClient(),
                httpsClient = new SSLClient();

            public Result doRequest(HttpRequestBase httpRequest, String decode, Set<Integer> expectStatuses) throws HttpException {
                if (Objects.equals("https", httpRequest.getURI().getScheme())) {
                    super.client = this.httpsClient;
                } else {
                    super.client = this.httpClient;
                }
                return super.doRequest(httpRequest, decode, expectStatuses);
            }
        };
    }

    /**
     * @param httpRequest    请求体
     * @param expectStatuses 期望的http返回码，不符合期望的时候抛出HttpException异常
     */
    public Result doRequest(HttpRequestBase httpRequest,String decode, Set<Integer> expectStatuses) throws HttpException {
        try {
            HttpResponse response = client.execute(httpRequest);
            if (response != null) {
                if (expectStatuses == null || expectStatuses.contains(response.getStatusLine().getStatusCode())) {
                    return new ResultImp(response, decode);
                } else {
                    throw new HttpException(response, decode);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            httpRequest.abort();
        }
        return null;
    }


    /**
     * @param httpRequest    请求体
     * @return 响应体
     */
    public Result doRequest(HttpRequestBase httpRequest, String decode) throws HttpException {
        return doRequest(httpRequest, decode, (Set<Integer>)null);
    }

//    /**
//     * @param httpRequest    请求体
//     * @param decode         body的解码
//     * @param expectStatuses 期望的http返回码，不符合期望的时候抛出HttpException异常
//     * @return 响应体
//     */
//    public String doRequestForBody(HttpRequestBase httpRequest, String decode, Set<Integer> expectStatuses) throws HttpException {
//        return doRequest(httpRequest, decode, expectStatuses).getBody();
//    }
//
//    public  String doRequestForBody(HttpRequestBase request, String decode) throws HttpException{
//        return doRequestForBody(request, decode, null);
//    }

    public CookieStore getCookies(){
        return client.getCookieStore();
    }

    public void setCookies(CookieStore cookies){
        client.setCookieStore(cookies);
    }
}