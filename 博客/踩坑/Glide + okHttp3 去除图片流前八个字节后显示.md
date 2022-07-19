

今天在学习群里碰到了一个问题：用Glide  + okHttp3加载图片，这个时候有一个需求就是，有一个url对应的图片流，这个图片数据流需要去除前面八个字节后才能正常显示图片，所以那位大佬的思路就是添加一个okHttp的应用层拦截器，并在该拦截器中对图片流前面的八个字节进行移除，于是就有了最开始的下面这段代码

```java
@GlideModule
public class MyOkHttpGlideModule extends AppGlideModule {
    @Override
    public void registerComponents(Context context, Glide glide, Registry registry) {
        Log.i("zhang_xin", "减去了若干字节00000000");
        //定制OkHttp
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
        //请求头设置
        httpClientBuilder.interceptors().add(new ReduceByteInterceptor());
        OkHttpClient okHttpClient = httpClientBuilder.build();
        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(okHttpClient));
    }


    public static class ReduceByteInterceptor implements Interceptor {
        public ReduceByteInterceptor() {
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response originalResponse = chain.proceed(request);
            ResponseBody body = originalResponse.body();
            // 获取
            InputStream inputStream = body.byteStream();
            inputStream.read();
            Log.i("Test", "减去了若干字节前" + originalResponse.body().byteStream().getClass().getName());
            inputStream.skip(7);
            Log.i("Test", "减去了若干字节后" + originalResponse.body().byteStream().getClass().getName());
            return originalResponse.newBuilder().body(body).build();
        }
        
    }
}
```

我们主要看一下ReduceByteInterceptor的intercept方法

```java
@Override
public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    Response originalResponse = chain.proceed(request);
    ResponseBody body = originalResponse.body();
    InputStream inputStream = body.byteStream();
    // 通过body.byteStream()获取的inputStream调用read和skip方法的时候都会操作body.source().buffer，具体原因看源码
    inputStream.read();
    inputStream.skip(7);

    return originalResponse.newBuilder().body(body).build();
}
```

实际上

```java
inputStream.read();
inputStream.skip(7);
```

等价于

```
inputStream.skip(8);
```

InputStream对象是通过ResponseBody.byteStream()获得的，调用该InputStream.read()方法的时候虽然会新建一个InputStream对象，但是无论哪个InputStream对象，操作的都是同一个ResponseBody.source().buffer，而ResponseBody.source().buffer就是网络实体数据的缓冲区。既然通过ResponseBody.byteStream()获取的InputStream对象操作的是ResponseBody.source().buffer，那我们可以直接操作buffer (直接操作buffer的话需要先调用InputStream.read()或者ResponseBody.source().readByte()，具体原因看源码，我是没怎么看懂，最好是不要这么使用) ，或者可以通过ResponseBody.source()操作buffer即可。

所以

```java
ResponseBody body = originalResponse.body();
InputStream inputStream = body.byteStream();
inputStream.read();
inputStream.skip(7);
```

等价于

```java
ResponseBody body = originalResponse.body();
// 这里便会直接操作buffer跳过8个字节
originalResponse.body().source().skip(8);
```





那这段代码运行得起来结果正确吗？

不正确！！

我们在Glide加一个载回调，打印出失败的信息

```java
Glide.with(this)
        .load("https://srcfiles-1301875640.cos.accelerate.myqcloud.com/movie/cn/yz/HKDOLL-014/HKDOLL-014.jpg")
        .skipMemoryCache(true)
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .addListener(new RequestListener<Drawable>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                Log.i("Shzy", "onLoadFailed: " + e.getMessage());
                return false;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                return false;
            }
        })
        .into(imageView);
```

我们看看结果

![image-20220125023812884](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220125023812884.png)

这是java的Io异常，异常原因就是：预期收到296947个字节数据，但实际上只收到了296939个字节的数据，因为我们在拦截器移除了前面八个字节。而296947也就是预期收到的字节数实际上就是ResponseBody.contentLength()，也就是响应体的总长度(和响应头的Content-Length字段对应)，而ResponseBody.contentLength属性是一个final对象我们没法直接修改，所以我们必须新建一个响应体，并保证contentLength和实际的内容长度一致

```java
// 构建一个新的请求体，目的是让 content-Length = 去除8字节后的
ResponseBody newResponseBody = ResponseBody
        .create(originalResponse.body().contentType(), bufferedSource.readByteArray());
return originalResponse.newBuilder().body(newResponseBody).build();
```



所以最后的代码应该是这样的

```java
@GlideModule
public class MyOkHttpGlideModule extends AppGlideModule {
    @Override
    public void registerComponents(Context context, Glide glide, Registry registry) {
        //定制OkHttp
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
        //请求头设置
        httpClientBuilder.addInterceptor(new ReduceByteInterceptor());
        OkHttpClient okHttpClient = httpClientBuilder.build();
        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(okHttpClient));
    }


    public static class ReduceByteInterceptor implements Interceptor {

        public ReduceByteInterceptor() {
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response originalResponse = chain.proceed(request);

            if(originalResponse.body() == null){
                return null;
            }
            BufferedSource bufferedSource = originalResponse.body().source();
            bufferedSource.skip(8);
            // 构建一个新的请求体，目的是让 contentLength 与 去除8字节后的内容长度一致
            ResponseBody newResponseBody = ResponseBody
                    .create(originalResponse.body().contentType(), bufferedSource.readByteArray());

            return originalResponse.newBuilder().body(newResponseBody).build();
        }

    }
}
```

