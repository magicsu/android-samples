package cn.eshifu.mclient.manager;

import android.graphics.drawable.Drawable;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.concurrent.TimeUnit;
import cn.eshifu.mclient.api.ApiManager;
import cn.eshifu.mclient.config.AppConfig;
import cn.eshifu.mclient.interceptor.TokenInterceptor;
import io.realm.RealmObject;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class RequestManager {
    private ApiManager apiManager;

    private static class RequestManagerHolder {
        final static RequestManager instance = new RequestManager();
    }

    public static ApiManager getApiManager() {
        return RequestManagerHolder.instance.apiManager;
    }

    public RequestManager() {
        init();
    }

    private void init() {
        OkHttpClient.Builder client = new OkHttpClient.Builder();
        client.connectTimeout(AppConfig.NET_CONNECT_TIMEOUT, TimeUnit.MILLISECONDS);
        client.readTimeout(AppConfig.NET_READ_TIMEOUT, TimeUnit.MILLISECONDS);

        // Token验证拦截器
        client.addInterceptor(new TokenInterceptor());

        // 日志
        if (AppConfig.DEBUG) {
            client.addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY));
        } else {
            client.addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC));
        }

        // Gson & Realm bug fixes: https://realm.io/docs/java/0.77.0/#gson
        Gson gson = new GsonBuilder()
                .setExclusionStrategies(new ExclusionStrategy() {
                    @Override
                    public boolean shouldSkipField(FieldAttributes f) {
                        return f.getDeclaringClass().equals(RealmObject.class) || f.getDeclaringClass().equals(Drawable.class);
                    }
                    @Override
                    public boolean shouldSkipClass(Class<?> clazz) {
                        return false;
                    }
                })
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(AppConfig.BASE_URL)
                .client(client.build())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build();

        apiManager = retrofit.create(ApiManager.class);
    }
}
