package org.ergoplatform.restapi.client;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import java.net.Proxy;
import java.time.format.DateTimeFormatter;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;
import org.ergoplatform.restapi.client.auth.HttpBasicAuth;
import org.ergoplatform.restapi.client.auth.ApiKeyAuth;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public class ApiClient {

  private String _hostUrl;
  private Map<String, Interceptor> apiAuthorizations;
  private OkHttpClient.Builder okBuilder;
  private Retrofit.Builder adapterBuilder;
  private JSON json;

  public Gson getGson() { return json.getGson(); }

  public ApiClient(String hostUrl) {
    _hostUrl = hostUrl;
    apiAuthorizations = new LinkedHashMap<String, Interceptor>();
    createDefaultAdapter();
  }

  public ApiClient(String hostUrl, String[] authNames) {
    this(hostUrl);
    for(String authName : authNames) {
      Interceptor auth;
      if ("ApiKeyAuth".equals(authName)) {
        auth = new ApiKeyAuth("header", "api_key");
      } else {
        throw new RuntimeException("auth name \"" + authName + "\" not found in available auth names");
      }

      addAuthorization(authName, auth);
    }
  }

  /**
   * Basic constructor for single auth name
   * @param authName Authentication name
   */
  public ApiClient(String hostUrl, String authName) {
    this(hostUrl, new String[]{authName});
  }

  /**
   * Helper constructor for single api key
   * @param authName Authentication name
   * @param apiKey API key
   */
  public ApiClient(String hostUrl, String authName, String apiKey) {
    this(hostUrl, authName);
    this.setApiKey(apiKey);
  }

  /**
   * Helper constructor for single basic auth or password oauth2
   * @param authName Authentication name
   * @param username Username
   * @param password Password
   */
  public ApiClient(String hostUrl, String authName, String username, String password) {
    this(hostUrl, authName);
    this.setCredentials(username,  password);
  }

  /**
   * Helper constructor for single password oauth2
   * @param authName Authentication name
   * @param clientId Client ID
   * @param secret Client Secret
   * @param username Username
   * @param password Password
   */
  public ApiClient(String hostUrl, String authName, String clientId, String secret, String username, String password) {
    this(hostUrl, authName);
  }

  public void createDefaultAdapter(Proxy proxy) {
    json = new JSON();
    okBuilder = new OkHttpClient.Builder();
    if (proxy != null) {
      okBuilder = okBuilder.proxy(proxy);
    }

    if (!_hostUrl.endsWith("/"))
      _hostUrl = _hostUrl + "/";

    adapterBuilder = new Retrofit
      .Builder()
      .baseUrl(_hostUrl)
      .addConverterFactory(ScalarsConverterFactory.create())
      .addConverterFactory(GsonCustomConverterFactory.create(json.getGson()));
  }

  public void createDefaultAdapter() {
    json = new JSON();
    okBuilder = new OkHttpClient.Builder();

    if (!_hostUrl.endsWith("/"))
      _hostUrl = _hostUrl + "/";

    adapterBuilder = new Retrofit
            .Builder()
            .baseUrl(_hostUrl)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonCustomConverterFactory.create(json.getGson()));
  }

  public <S> S createService(Class<S> serviceClass) {
    return adapterBuilder
      .client(okBuilder.build())
      .build()
      .create(serviceClass);
  }

  public ApiClient setDateFormat(DateFormat dateFormat) {
    this.json.setDateFormat(dateFormat);
    return this;
  }

  public ApiClient setSqlDateFormat(DateFormat dateFormat) {
    this.json.setSqlDateFormat(dateFormat);
    return this;
  }

  public ApiClient setOffsetDateTimeFormat(DateTimeFormatter dateFormat) {
    this.json.setOffsetDateTimeFormat(dateFormat);
    return this;
  }

  public ApiClient setLocalDateFormat(DateTimeFormatter dateFormat) {
    this.json.setLocalDateFormat(dateFormat);
    return this;
  }


  /**
   * Helper method to configure the first api key found
   * @param apiKey API key
   * @return ApiClient
   */
  public ApiClient setApiKey(String apiKey) {
    for(Interceptor apiAuthorization : apiAuthorizations.values()) {
      if (apiAuthorization instanceof ApiKeyAuth) {
        ApiKeyAuth keyAuth = (ApiKeyAuth) apiAuthorization;
        keyAuth.setApiKey(apiKey);
        return this;
      }
    }
    return this;
  }

  /**
   * Helper method to configure the username/password for basic auth or password oauth
   * @param username Username
   * @param password Password
   * @return ApiClient
   */
  public ApiClient setCredentials(String username, String password) {
    for(Interceptor apiAuthorization : apiAuthorizations.values()) {
      if (apiAuthorization instanceof HttpBasicAuth) {
        HttpBasicAuth basicAuth = (HttpBasicAuth) apiAuthorization;
        basicAuth.setCredentials(username, password);
        return this;
      }
    }
    return this;
  }

  /**
   * Helper method to pre-set the oauth access token of the first oauth found in the apiAuthorizations (there should be only one)
   * @param accessToken Access token
   * @return ApiClient
   */
  public ApiClient setAccessToken(String accessToken) {
    return this;
  }

  /**
   * Adds an authorization to be used by the client
   * @param authName Authentication name
   * @param authorization Authorization interceptor
   * @return ApiClient
   */
  public ApiClient addAuthorization(String authName, Interceptor authorization) {
    if (apiAuthorizations.containsKey(authName)) {
      throw new RuntimeException("auth name \"" + authName + "\" already in api authorizations");
    }
    apiAuthorizations.put(authName, authorization);
    okBuilder.addInterceptor(authorization);
    return this;
  }

  public Map<String, Interceptor> getApiAuthorizations() {
    return apiAuthorizations;
  }

  public ApiClient setApiAuthorizations(Map<String, Interceptor> apiAuthorizations) {
    this.apiAuthorizations = apiAuthorizations;
    return this;
  }

  public Retrofit.Builder getAdapterBuilder() {
    return adapterBuilder;
  }

  public ApiClient setAdapterBuilder(Retrofit.Builder adapterBuilder) {
    this.adapterBuilder = adapterBuilder;
    return this;
  }

  public OkHttpClient.Builder getOkBuilder() {
    return okBuilder;
  }

  public void addAuthsToOkBuilder(OkHttpClient.Builder okBuilder) {
    for(Interceptor apiAuthorization : apiAuthorizations.values()) {
      okBuilder.addInterceptor(apiAuthorization);
    }
  }

  /**
   * Clones the okBuilder given in parameter, adds the auth interceptors and uses it to configure the Retrofit
   * @param okClient An instance of OK HTTP client
   */
  public void configureFromOkclient(OkHttpClient okClient) {
    this.okBuilder = okClient.newBuilder();
    addAuthsToOkBuilder(this.okBuilder);
  }

  public <T> T cloneDataObject(T dataObj) {
    return (T)getGson().fromJson(getGson().toJson(dataObj), dataObj.getClass());
  }
}

/**
 * This wrapper is to take care of this case:
 * when the deserialization fails due to JsonParseException and the
 * expected type is String, then just return the body string.
 */
class GsonResponseBodyConverterToString<T> implements Converter<ResponseBody, T> {
  private final Gson gson;
  private final Type type;

  GsonResponseBodyConverterToString(Gson gson, Type type) {
    this.gson = gson;
    this.type = type;
  }

  @Override public T convert(ResponseBody value) throws IOException {
    String returned = value.string();
    try {
      return gson.fromJson(returned, type);
    }
    catch (JsonParseException e) {
      return (T) returned;
    }
  }
}

class GsonCustomConverterFactory extends Converter.Factory
{
  private final Gson gson;
  private final GsonConverterFactory gsonConverterFactory;

  public static GsonCustomConverterFactory create(Gson gson) {
    return new GsonCustomConverterFactory(gson);
  }

  private GsonCustomConverterFactory(Gson gson) {
    if (gson == null)
      throw new NullPointerException("gson == null");
    this.gson = gson;
    this.gsonConverterFactory = GsonConverterFactory.create(gson);
  }

  @Override
  public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
    if (type.equals(String.class))
      return new GsonResponseBodyConverterToString<Object>(gson, type);
    else
      return gsonConverterFactory.responseBodyConverter(type, annotations, retrofit);
  }

  @Override
  public Converter<?, RequestBody> requestBodyConverter(Type type, Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Retrofit retrofit) {
    return gsonConverterFactory.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit);
  }
}
