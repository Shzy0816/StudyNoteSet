

协议请求头
Expect:100-continue
1.用于询问Server是否愿意接受POST数据
2.接收到Server返回的100-continue应答以后, 才把数据POST给Server





疑点
1.RealCall中的三个队列：running,Ready还有啥？
2.RealInterceptorChain的exchange是干什么的？

# 一.拦截器链



# 二.拦截器

## 1.五种拦截器介绍

### 1）RetryAndFollowUpInterceptor - 重定向拦截器

这种拦截器属于应用程序类拦截器，它用于处理 重定向 或者 网络请求过程失败时的重试

### 2）BridgeInterceptor - 桥接拦截器

这种拦截器用于处理构建RequestHeader请求头类

### 3）CacheInterceptor - 缓存拦截器

该拦截器用于处理请求数据的缓存
在客户端向服务器发起一次请求会经过缓存拦截器：
1.缓存拦截器会判断是否存有该次请求url的缓存数据
	1）有，直接从缓存中读取数据，网络请求中断
	2）没有的话，缓存拦截器会放行该次请求，等到获取服务器的响应之后，将从服务器响应的数据进行缓存

### 4）ConnectInterceptor - 连接拦截器

1.在这里主要会创建RealConnection对象，该对象用于创建客户端与服务器之间的套接字连接。
2.调用RealConnection的newCodec方法，该方法会根据url类型（Http1 ， Http2）生成不同的ExchangeCodec（Http1ExchangeCodec，Http2ExchangeCodec）并将其交由Exchange管理。

而ExchangeCodec负责向服务器和客户端之间的套接字缓冲区读写数据

### 5）CallServerInterceptor - 呼叫服务拦截器

这里就是操作ExchangeCodec向套接字缓冲区读写数据



## 2.五种拦截器源码分析

### 1.RetryAndFollowUpInterceptor - 重定向拦截器

作用：重定向，最多不超过20次

#### 1）intercept方法分析

```java
override fun intercept(chain: Interceptor.Chain): Response {
  //获取拦截器链
  val realChain = chain as RealInterceptorChain
  var request = chain.request
  val call = realChain.call
  var followUpCount = 0
  var priorResponse: Response? = null
  var newExchangeFinder = true
  var recoveredFailures = listOf<IOException>()
  while (true) 
    // 如果newExchangeFinder == true，这里会创建一个Exchange对象
    call.enterNetworkInterceptorExchange(request, newExchangeFinder)
    var response: Response
    var closeActiveExchange = true 
    try {
      // 如果中途取消请求，则抛出异常，改异常不会再该方法中捕获，所以会直接跳出该方法
      if (call.isCanceled()) {
        throw IOException("Canceled")
      }

      try {
        // 调用拦截器链获取response
        response = realChain.proceed(request)
        newExchangeFinder = true
      } catch (e: RouteException) {
        // 连接路由失败
        if (!recover(e.lastConnectException, call, request, requestSendStarted = false)) {
          throw e.firstConnectException.withSuppressed(recoveredFailures)
        } else {
          recoveredFailures += e.firstConnectException
        }
        // 无需再新建一个Exchage
        newExchangeFinder = false
        // 重试
        continue
      } catch (e: IOException) {
        // 连接服务器失败
        if (!recover(e, call, request, requestSendStarted = e !is ConnectionShutdownException))             
        {
          throw e.withSuppressed(recoveredFailures)
        } else {
          recoveredFailures += e
        }
        // 无需再新建一个Exchage
        newExchangeFinder = false
        // 重试
        continue
      }

      // Attach the prior response if it exists. Such responses never have a body.
      if (priorResponse != null) {
        response = response.newBuilder()
            .priorResponse(priorResponse.newBuilder()
                .body(null)
                .build())
            .build()
      }

     
      val exchange = call.interceptorScopedExchange
      // 根据response的返回码生成重定向Request即followUp
      val followUp = followUpRequest(response, exchange)

      // followUp == null表示无需进行下一步也就是重定向请求。直接返回response
      if (followUp == null) {
        if (exchange != null && exchange.isDuplex) {
          call.timeoutEarlyExit()
        }
        // 将该标志位置为false，表示无需关闭exchange
        closeActiveExchange = false
        return response
      }

      val followUpBody = followUp.body
      if (followUpBody != null && followUpBody.isOneShot()) {
        // 将该标志位置为false，表示无需关闭exchange
        closeActiveExchange = false
        return response
      }
      
      // 走到这一步，说明还需要进行重定向请求，也就是该response不是客户端想要的响应，所以先关闭该response.body
      response.body?.closeQuietly()
       
      // 如果尝试次数超过MAX_FOLLOW_UPS，也就是20次，那么直接抛出异常（这个异常不会在该方法中被捕获）
      if (++followUpCount > MAX_FOLLOW_UPS) {
        throw ProtocolException("Too many follow-up requests: $followUpCount")
      }
      
      // 将request换成重定向后的请求
      request = followUp
      // 保存上一个response
      priorResponse = response
    } finally {
      // 根据closeActiveExchange的值判断是否需要关闭exchange，每次循环的最后都会执行
      call.exitNetworkInterceptorExchange(closeActiveExchange)
    }
  }
}
```

梳理一下流程：
先来了解一下ExchangeFinder：
ExchangeFinder在网络请求开始前，会根据request中的url的主机字段寻找到一个RealConnection，进而通过RealConnection获取到ExchangeCodec对象。
RealConnection：对应一个套接字连接
ExchangeCodec：用于向套接字缓冲区读写数据

开启一个循环用作**重定向请求**和**网络请求失败后的重试**

1. 根据newExchangeFinder标识符的值创建一个ExchangeFinder,这里会有两种情况

   (1) 请求因为异常(连接路由异常或者连接服务器异常)返回，该异常会被捕获然后调用continue进行下一次循环也就是**网络请求失败后的重试**，这个时候request并没有改变，所以ExchangeFinder不用改变，newExchangeFinder = false
   (2) 请求成功后response，检查该response的状态码，若状态码为3 * *（比如301)，会根据response新建一个request（代码中的followUp），随后进行下一次循环也就是执行**重定向请求**，而由于此时request已经不再是上一次请求的request，所以ExchangeFinder也应该重建，newExchangeFinder = true

2. 调用拦截器链获取response
   (1) 若出现异常（路由选择异常或连接服务器异常   --- >   进入下一次循环进行**网络请求失败后的重试**

   (2) 若成功获取到response，检查response的状态码

   ​	【1】 若response的状态码为3 * * ，表明需要进行重定向操作，此时会根据response生成新的request然后进入下一个循环开始执行**重定向请求**

   ​    【2】 否则不需要重定向，return response。



### 2.BridgeInterceptor - 重定向拦截器

```java
@Throws(IOException::class)
override fun intercept(chain: Interceptor.Chain): Response {
  val userRequest = chain.request()
  val requestBuilder = userRequest.newBuilder()

  val body = userRequest.body
  if (body != null) {
    val contentType = body.contentType()
    if (contentType != null) {
      requestBuilder.header("Content-Type", contentType.toString())
    }

    val contentLength = body.contentLength()
    if (contentLength != -1L) {
      requestBuilder.header("Content-Length", contentLength.toString())
      requestBuilder.removeHeader("Transfer-Encoding")
    } else {
      requestBuilder.header("Transfer-Encoding", "chunked")
      requestBuilder.removeHeader("Content-Length")
    }
  }

  if (userRequest.header("Host") == null) {
    requestBuilder.header("Host", userRequest.url.toHostHeader())
  }

  if (userRequest.header("Connection") == null) {
    requestBuilder.header("Connection", "Keep-Alive")
  }

  // If we add an "Accept-Encoding: gzip" header field we're responsible for also decompressing
  // the transfer stream.
  var transparentGzip = false
  if (userRequest.header("Accept-Encoding") == null && userRequest.header("Range") == null) {
    transparentGzip = true
    requestBuilder.header("Accept-Encoding", "gzip")
  }

  val cookies = cookieJar.loadForRequest(userRequest.url)
  if (cookies.isNotEmpty()) {
    requestBuilder.header("Cookie", cookieHeader(cookies))
  }

  if (userRequest.header("User-Agent") == null) {
    requestBuilder.header("User-Agent", userAgent)
  }

  val networkResponse = chain.proceed(requestBuilder.build())

  cookieJar.receiveHeaders(userRequest.url, networkResponse.headers)

  val responseBuilder = networkResponse.newBuilder()
      .request(userRequest)

  if (transparentGzip &&
      "gzip".equals(networkResponse.header("Content-Encoding"), ignoreCase = true) &&
      networkResponse.promisesBody()) {
    val responseBody = networkResponse.body
    if (responseBody != null) {
      val gzipSource = GzipSource(responseBody.source())
      val strippedHeaders = networkResponse.headers.newBuilder()
          .removeAll("Content-Encoding")
          .removeAll("Content-Length")
          .build()
      responseBuilder.headers(strippedHeaders)
      val contentType = networkResponse.header("Content-Type")
      responseBuilder.body(RealResponseBody(contentType, -1L, gzipSource.buffer()))
    }
  }

  return responseBuilder.build()
}
```

### 3.BridgeInterceptor - 缓存拦截器

```java
override fun intercept(chain: Interceptor.Chain): Response {
  val call = chain.call()
  val cacheCandidate = cache?.get(chain.request())

  val now = System.currentTimeMillis()

  val strategy = CacheStrategy.Factory(now, chain.request(), cacheCandidate).compute()
  val networkRequest = strategy.networkRequest
  val cacheResponse = strategy.cacheResponse

  cache?.trackResponse(strategy)
  val listener = (call as? RealCall)?.eventListener ?: EventListener.NONE

  if (cacheCandidate != null && cacheResponse == null) {
    // The cache candidate wasn't applicable. Close it.
    cacheCandidate.body?.closeQuietly()
  }

  // If we're forbidden from using the network and the cache is insufficient, fail.
  if (networkRequest == null && cacheResponse == null) {
    return Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(HTTP_GATEWAY_TIMEOUT)
        .message("Unsatisfiable Request (only-if-cached)")
        .body(EMPTY_RESPONSE)
        .sentRequestAtMillis(-1L)
        .receivedResponseAtMillis(System.currentTimeMillis())
        .build().also {
          listener.satisfactionFailure(call, it)
        }
  }

  // If we don't need the network, we're done.
  if (networkRequest == null) {
    return cacheResponse!!.newBuilder()
        .cacheResponse(stripBody(cacheResponse))
        .build().also {
          listener.cacheHit(call, it)
        }
  }

  if (cacheResponse != null) {
    listener.cacheConditionalHit(call, cacheResponse)
  } else if (cache != null) {
    listener.cacheMiss(call)
  }

  var networkResponse: Response? = null
  try {
    networkResponse = chain.proceed(networkRequest)
  } finally {
    // If we're crashing on I/O or otherwise, don't leak the cache body.
    if (networkResponse == null && cacheCandidate != null) {
      cacheCandidate.body?.closeQuietly()
    }
  }

  // If we have a cache response too, then we're doing a conditional get.
  if (cacheResponse != null) {
    if (networkResponse?.code == HTTP_NOT_MODIFIED) {
      val response = cacheResponse.newBuilder()
          .headers(combine(cacheResponse.headers, networkResponse.headers))
          .sentRequestAtMillis(networkResponse.sentRequestAtMillis)
          .receivedResponseAtMillis(networkResponse.receivedResponseAtMillis)
          .cacheResponse(stripBody(cacheResponse))
          .networkResponse(stripBody(networkResponse))
          .build()

      networkResponse.body!!.close()

      // Update the cache after combining headers but before stripping the
      // Content-Encoding header (as performed by initContentStream()).
      cache!!.trackConditionalCacheHit()
      cache.update(cacheResponse, response)
      return response.also {
        listener.cacheHit(call, it)
      }
    } else {
      cacheResponse.body?.closeQuietly()
    }
  }

  val response = networkResponse!!.newBuilder()
      .cacheResponse(stripBody(cacheResponse))
      .networkResponse(stripBody(networkResponse))
      .build()

  if (cache != null) {
    if (response.promisesBody() && CacheStrategy.isCacheable(response, networkRequest)) {
      // Offer this request to the cache.
      val cacheRequest = cache.put(response)
      return cacheWritingResponse(cacheRequest, response).also {
        if (cacheResponse != null) {
          // This will log a conditional cache miss only.
          listener.cacheMiss(call)
        }
      }
    }

    if (HttpMethod.invalidatesCache(networkRequest.method)) {
      try {
        cache.remove(networkRequest)
      } catch (_: IOException) {
        // The cache cannot be written.
      }
    }
  }

  return response
}
```

### 4.ConnectInterceptor

在okHttp拦截器责任链中，ConnectInterceptor是责任链中的第四个拦截器。它属于网络层拦截器，所以我们主要看一下它的Interceptor方法

#### 1）ConnectInterceptor.interceptor

```java
object ConnectInterceptor : Interceptor {
  @Throws(IOException::class)
  override fun intercept(chain: Interceptor.Chain): Response {
    // 获取拦截器链
    val realChain = chain as RealInterceptorChain
    // 获取exchange对象
    val exchange = realChain.call.initExchange(chain)
    // 复制拦截器链，并且设置exchange
    val connectedChain = realChain.copy(exchange = exchange)
    // 传递责任给下一个拦截器也就是CallServerInterceptor
    return connectedChain.proceed(realChain.request)
  }
}
```

exchagne(Exchange)，它用于向一次连接的套接字缓冲区中读写数据，也就是和目标服务器进行数据交换。
应用拦截器中Exchange属性为是null,因为它们不需要和目标服务器进行数据交流。
网络拦截器的Exchange不能为null，因为它们负责目标服务器进行数据交流。

- 所以前面的三个拦截器RetryAndFollowUpInterceptor，BridgeInterceptor，CacheInterceptor都属于应用拦截器，所以它们的exchange属性为null，在传递责任链的时候就不会传递exchange对象。
- 而拦截器责任链中从ConnectInterceptor以后的拦截器都属于网络拦截器，所以它们必须拥有一个exchange对象，exchange也是在ConnectInterceptor拦截器中创建

结合`val exchange = realChain.call.initExchange(chain)`，我们来看看exchage是如何创建的

#### 2）RealCallback.initExchange

```java
internal fun initExchange(chain: RealInterceptorChain): Exchange {
    synchronized(this) {
      check(expectMoreExchanges) { "released" }
      check(!responseBodyOpen)
      check(!requestBodyOpen)
    }
    // 这个exchangeFinder就是在重定向拦截器中创建的那个
    val exchangeFinder = this.exchangeFinder!!
    // codec是一个ExchangeCodec,它是Exchange中真正负责和目标服务器进行数据交流的对象
    val codec = exchangeFinder.find(client, chain)
    // 创建Exchange，并将codec作为参数传入，Exchange会持有一个ExchangeCodec对象
    val result = Exchange(this, eventListener, exchangeFinder, codec)
    this.interceptorScopedExchange = result
    this.exchange = result
    synchronized(this) {
      this.requestBodyOpen = true
      this.responseBodyOpen = true
    }

    if (canceled) throw IOException("Canceled")
    // 返回result，也就是Exchange对象
    return result
  }
```

这里除了注释，我们重点关注一下这两行代码: 

- `val codec = exchangeFinder.find(client, chain)`这行代码，codec是一个ExchangeCodec对象，它是真正负责与目标服务器进行数据交流(向套接字缓冲区中读写数据)的对象

- `val result = Exchange(this, eventListener, exchangeFinder, codec)`而这行代码创建了一个Exchange对象，并把ExchangeCodec实例codec作为参数传入，所以Exchange在创建完之后会持有一个ExchangeCodec，并将于目标服务器进行数据通讯(向套接字缓冲区读写数据)的任务交给它



这里的重点是`val codec = exchangeFinder.find(client, chain)`如何通过exchangeFinder的find方法找到一个codec对象，我们来看看ExchangeFinder.find方法

#### 3）ExchangeFInder.find

```java
fun find(
  client: OkHttpClient,
  chain: RealInterceptorChain
): ExchangeCodec {
  try {
    //寻找到一个健康的连接也就是健康的RealConnection对象
    val resultConnection = findHealthyConnection(
        connectTimeout = chain.connectTimeoutMillis,
        readTimeout = chain.readTimeoutMillis,
        writeTimeout = chain.writeTimeoutMillis,
        pingIntervalMillis = client.pingIntervalMillis,
        connectionRetryEnabled = client.retryOnConnectionFailure,
        doExtensiveHealthChecks = chain.request.method != "GET"
    )
    // 调用寻找的RealConnection对象的newCodec方法创建一个ExchangeCodec对象
    return resultConnection.newCodec(client, chain)
  } catch (e: RouteException) {
    trackFailure(e.lastConnectException)
    throw e
  } catch (e: IOException) {
    trackFailure(e)
    throw RouteException(e)
  }
}
```

RealConnection类对象对应一个于目标服务器的套接字连接，也就是对应一个Request，在同一个时间上，同一个RealConnection对应一个Request，但RealConnection可以复用，所以在不同时间上，同一个RealConnection可以对应不同的Request

该方法的核心就是通过findHealthyConnection找到一个健康的RealConnection对象，然后通过RealConnection对象的newCodec方法创建出一个ExchangeCoedec对象

我们来看看ExchangeFInder.findHealthyConnection

#### 4）ExchangeFInder.findHealthyConnection

```java
private fun findHealthyConnection(
  connectTimeout: Int,
  readTimeout: Int,
  writeTimeout: Int,
  pingIntervalMillis: Int,
  connectionRetryEnabled: Boolean,
  doExtensiveHealthChecks: Boolean
): RealConnection {
  // 一个死循环
  while (true) {
    // 通过findCConnection方法获取到一个RealConnection
    val candidate = findConnection(
        connectTimeout = connectTimeout,
        readTimeout = readTimeout,
        writeTimeout = writeTimeout,
        pingIntervalMillis = pingIntervalMillis,
        connectionRetryEnabled = connectionRetryEnabled
    )

    // 判断该连接是否健康，如果健康直接返回
    if (candidate.isHealthy(doExtensiveHealthChecks)) {
      return candidate
    }

    // 如果连接不健康，它将会从连接池中被移除
    candidate.noNewExchanges()
 
    // 如果还有下一个路由，那么循环继续
    if (nextRouteToTry != null) continue
  
    // 如果还有下一组路由，那么循环继续
    val routesLeft = routeSelection?.hasNext() ?: true
    if (routesLeft) continue
    
    // 如果还有下一个路由选择器，那么循环继续
    val routesSelectionLeft = routeSelector?.hasNext() ?: true
    if (routesSelectionLeft) continue

    // 若果走到这，说明已经遍历完所有的路由，而且没找到可用的连接，那么将抛出异常
    throw IOException("exhausted all routes")
  }
}
```

#### 5）ExchangeFInder.findConnection

```java
@Throws(IOException::class)
  private fun findConnection(
    connectTimeout: Int,
    readTimeout: Int,
    writeTimeout: Int,
    pingIntervalMillis: Int,
    connectionRetryEnabled: Boolean
  ): RealConnection {
    if (call.isCanceled()) throw IOException("Canceled")

    // 1. 尝试重用RealCall中的RealConnection对象，
    val callConnection = call.connection 
    if (callConnection != null) {
      var toClose: Socket? = null
      synchronized(callConnection) {
         // 1.1 如果noNewExchanges为true（表示连接以及关闭），或者主机名和端口不匹配，那么将关闭该该连接的套接字。这里先将该连接套接字对象赋值给toClose变量
        if (callConnection.noNewExchanges || !sameHostAndPort(callConnection.route().address.url)) {
          toClose = call.releaseConnectionNoEvents()
        }
      }

      // 1.2 toClose为null表示该call的连接仍可进行复用，直接return
      if (call.connection != null) {
        check(toClose == null)
        return callConnection
      }

      // 1.3 如果toClose不会null，表示该连接不合适，那么将关闭该连接的套接字
      toClose?.closeQuietly()
      eventLi stener.connectionReleased(call, callConnection)
    }

    
    // 2. 这个时候需要为call设置一个新的连接
    
    // 2.1 先将部分变量初始化
    refusedStreamCount = 0
    connectionShutdownCount = 0
    otherFailureCount = 0

    // 2.2 尝试从连接池中找寻一个合适的连接，这个时候address还未进行dns解析，所以第三个参数routes传入null（也就是第三个参数其实是address通过解析得到的routes）
    if (connectionPool.callAcquirePooledConnection(address, call, null, false)) {
      // callAcquirePooledConnection方法如果返回true，那么call的connection属性将会指向从连接池中找到的connection
      val result = call.connection!!
      eventListener.connectionAcquired(call, result)
      return result
    }

    // 2.3 如果在连接池中没有找到合适的连接，那么就找寻和一个合适的路由创建一个新的连接
    val routes: List<Route>?
    val route: Route
    if (nextRouteToTry != null) {
      // Use a route from a preceding coalesced connection.
      routes = null
      route = nextRouteToTry!!
      nextRouteToTry = null
    } else if (routeSelection != null && routeSelection!!.hasNext()) {
      // Use a route from an existing route selection.
      routes = null
      route = routeSelection!!.next()
    } else {
      // Compute a new route selection. This is a blocking operation!
      var localRouteSelector = routeSelector
      if (localRouteSelector == null) {
        localRouteSelector = RouteSelector(address, call.client.routeDatabase, call, eventListener)
        this.routeSelector = localRouteSelector
      }
      val localRouteSelection = localRouteSelector.next()
      routeSelection = localRouteSelection
      routes = localRouteSelection.routes

      if (call.isCanceled()) throw IOException("Canceled")

      
      // 对address进行域名解析得到routes，再去连接池中查找是否有合适的连接可以使用，这个时候第三个参数传入routes
      if (connectionPool.callAcquirePooledConnection(address, call, routes, false))           			{
        val result = call.connection!!
        eventListener.connectionAcquired(call, result)
         // 找到了直接返回该连接
        return result
      }
      
      // 没找到的话就从RouteSelection的Routes列表中拿一个route
      route = localRouteSelection.next()
    }

    // 根据上面获取到的route创建一个新的连接
    val newConnection = RealConnection(connectionPool, route)
    call.connectionToCancel = newConnection
    try {
      // 调用新连接的connect方法连接服务器，这里进行了TCP + TLS握手，后面再作分析，该操作会是个阻塞操作
      newConnection.connect(
          connectTimeout,
          readTimeout,
          writeTimeout,
          pingIntervalMillis,
          connectionRetryEnabled,
          call,
          eventListener
      )
    } finally {
      call.connectionToCancel = null
    }
    call.client.routeDatabase.connected(newConnection.route())

    // 第三次从连接池中查找，newConnection.connect会阻塞一段时间，在这段时间可能其它的请求可能会创建正好适合当前请求的连接并加入到了连接池，因为Http2协议是支持多路复用的，即一个连接执行多个请求，若能从第三次查找中找到合适的连接，那么可以关闭之前刚刚创建的连接
    if (connectionPool.callAcquirePooledConnection(address, call, routes, true)) {
      val result = call.connection!!
      nextRouteToTry = route
      newConnection.socket().closeQuietly()
      eventListener.connectionAcquired(call, result)
      return result
    }

    synchronized(newConnection) {
      // 将新建的连接放入连接池
      connectionPool.put(newConnection)
      // 将call加入新建的连接池
      call.acquireConnectionNoEvents(newConnection)
    }

    eventListener.connectionAcquired(call, newConnection)
    return newConnection
  }
```

我们先重点关注一下`connectionPool.callAcquirePooledConnection(address, call, null, false)`，这里的connection是一个RealConnectionPool实例

##### 【1】RealConnectionPool.callAcquirePooledConnection

```java
fun callAcquirePooledConnection(
  address: Address,
  call: RealCall,
  routes: List<Route>?,
  requireMultiplexed: Boolean
): Boolean {
  // 遍历连接池中的连接
  for (connection in connections) {
    synchronized(connection) {
      // 是否进行多路复用操作
      if (requireMultiplexed && !connection.isMultiplexed) return@synchronized
      // 根据address和routes找到合适的连接
      if (!connection.isEligible(address, routes)) return@synchronized
      call.acquireConnectionNoEvents(connection)
      return true
    }
  }
  return false
}
```

return@synchronized 在这里可以看成是跳出synchronized闭包，相当于一次continue

在第一个判断语句`if (requireMultiplexed && !connection.isMultiplexed)`中，
requireMultiplexed是外部传入的参数，表示使用者想获取一个能够进行多路复用的连接RealConnection对象，
1.如果该连接是Http2连接，connection.isMultiplexed的值一定是true，所以无论如何都可以通过该if语句
2.如果该连接是Http1连接，那么connection.isMultiplexed的值一定是false，这时候requireMultiplexed的值为false才可以通过该语句，也就是Http1无法实现连接的复用。

第二个判断语句中`if (!connection.isEligible(address, routes))`
主要是判断该连接是否适合address 以及address通过dns解析出的routes，如果不适合那么将跳出该次循环
来看看`RealConnection.isEligible()`方法

###### 「1」RealConnection.isEligible

```java
internal fun isEligible(address: Address, routes: List<Route>r): Boolean {
  assertThreadHoldsLock()

  // 当该连接的calls队列数量到达最大值 或者 noNewExchanges为true(为true表示该连接已经关闭)时，return false 表示该连接不是合适的连接
  if (calls.size >= allocationLimit || noNewExchanges) return false

  // 当该连接的route（一个连接会有一个route）的address.url的非主机字段 和 参数中的address不一样，返回false表示该连接不是合适的连接
  if (!this.route.address.equalsNonHost(address)) return false

  // 如果参数中的adderss.url主机字段 和 route中的address.url主机字段相同，那么这是一个很完美的连接，所以return true
  if (address.url.host == this.route().address.url.host) {
    return true 
  }

 
  //代码执行到这，说明非主机字段相同，但是主机字段不相同，这个时候若连接合并后满足要求，这仍然是一个合适的连接。下面的代码是针对Https(Http版本为2.0)请求的，不满足这个条件可以直接视为reutrn 

  // 1. 首先该连接需要是http2连接，如果Https使用了Http2，那么对于每一个使用Http2的源，会有一个对应的服务器证书，其中包含名称(主机名)列表或者主机名通配符(比如"*.example.com")，这些符合条件的主机名都会具有权威性，服务器会对这些主机名做出响应
  if (http2Connection == null) return false

  // 2. routes(参数address通过DNS解析得到的route列表）中的任意一个route能够匹配该connection的route，那么进行下一步认证。否则说明该connection不适合该address，直接返回false
  if (routes == null || !routeMatchesAny(routes)) return false

  // 3. 若上一步在routes中找到匹配的route，说明该connection适合该address，但是需要对该address进行证书认证
  if (address.hostnameVerifier !== OkHostnameVerifier) return false
  if (!supportsUrl(address.url)) return false

  // 4. 证书锁定必须匹配该主机
  try {
    address.certificatePinner!!.check(address.url.host, handshake()!!.peerCertificates)
  } catch (_: SSLPeerUnverifiedException) {
    return false
  }
  
  // 该连接能够执行该call
  return true
}
```

该方法用于判断RealConnection是否适合用于参数中的address。而传入的routes是address经过DNS解析后得到的route列表。
结合注释总结一下该方法的流程：

1. 判断该连接执行的call数量是否达到最大值，或者该连接是否已经关闭，满足任意一个条件都说明该连接不可用

2. 比较 **该连接的route属性的route.address的url** 和 **参数中的adderss 的url** ，判断这两个url的非主机字段是否相同，不相同说明该连接不适合用于参数中的address

3. 如果两个url非主机部分相同，比较两个url的主机字段
   （1）相同，那这是一个很完美的连接,返回true

   （2）不相同 
    		「1」如果不是 Http版本号为2的Http2，那么该连接不适合参数中的address

   ​		 「2」如果是Https并且Http版本号为2，那么遍历参数routes(由address经过DNS解析得到的route列表)
   ​					<1> 若能从routes中找到与连接的route相匹配的route，那么再通过证书比较后即说明该连接适用
   ​					<2> 若不能，说明该连接不适用于参数中的address

最后就是`call.acquireConnectionNoEvents(connection)`我们可以先来看看它的源码

###### 「2」RealCall.acquireConnectionNoEvents

```java
fun acquireConnectionNoEvents(connection: RealConnection) {
  connection.assertThreadHoldsLock()
    
  check(this.connection == null)
  // 将RealCall的connection属性设置成即将加入的connection对象，表示RealCall位于这个connection中
  this.connection = connection
  // 将RealCall(this)加入Connection的calls队列中
  connection.calls.add(CallReference(this, callStackTrace))
}
```

总的来说就是将call加入connection的calls队列当中，并且call会持有该connection的一个引用，表示该call位于该connection当中

###### 「3」整体流程

遍历连接池中的连接，逐个判断：

1. 该连接是否进行多路复用操作（一个连接执行多个请求，只有Http2版本才行），以及该连接是否支持多路复用操作。
2. 该连接是否适合该地址
3. 如果该连接适合该地址，将该地址加入连接的calls列表当中



回到ExchangeFInder.find()方法当中看看这行代码：`val localRouteSelection = localRouteSelector.next()`，它是在没找到合适的route的时候，创建了一个新的RouterSelector，然后调用 RouterSelector.next()方法获取到一个RouterSelection对象，RouteSelection会只有一个Routes，也就是Route列表。可以看一下RouteSelection.next()中的逻辑

##### 【2】RouteSelector.next

```java
java@Throws(IOException::class)
operator fun next(): Selection {
  if (!hasNext()) throw NoSuchElementException()
  //  新建一个Route列表
  val routes = mutableListOf<Route>()
  //  这里就是遍历RouteSelector的proxies属性，这是一个Proxy列表  
  while (hasNextProxy()) {
    // 获取到下一个Proxy，这里同时也会找到下一组inetSocketAddresses
    val proxy = nextProxy()
    // 遍历inetSocketAddresses，创建出相应的route并加入routes中
    for (inetSocketAddress in inetSocketAddresses) {
      val route = Route(address, proxy, inetSocketAddress)
      if (routeDatabase.shouldPostpone(route)) {
        postponedRoutes += route
      } else {
        routes += route
      }
    }
    // 如果routes列表不为空，跳出while循环
    if (routes.isNotEmpty()) {
      break
    }
  }
  // 这里体现了先使用能用的routes，在尝试失败的route ？？？？？？？？？？？？？？？？？？待完善？
  if (routes.isEmpty()) {
    routes += postponedRoutes
    postponedRoutes.clear()
  }
  
  // 用routes列表构建出新的RouteSelection对象并返回
  return Selection(routes)
}
```

1.遍历Proxies列表，得到每一个Proxy 以及 Proxy对应的inetSocketAddresses(inetSocketAddress列表)
2.遍历inetSocketAddresses列表，为inetSocketAddresses中的每一个inetSocketAddress创建一个route，并加入routes中

这里比较核心的是 `val proxy = nextProxy()` 这句代码不仅会获得下一个proxy，也会将该proxy对应的一组inetSocketAddress保存到属性inetSocketAddresses中

###### 「1」RouteSelector.nextProxy()方法

```java
private fun nextProxy(): Proxy {
  if (!hasNextProxy()) {
    throw SocketException(
        "No route to ${address.url.host}; exhausted proxy configurations: $proxies")
  }
  // 获取到下一个Proxy，并且将 nextProxyIndex + 1，nextProxyIndex是RouteSelector的属性，用于记录当前使用的是proxies中的哪一个proxy
  val result = proxies[nextProxyIndex++]
  // 重置下一组InetSocketAddress，这会直接改变RouteSelector的InetSocketAddress属性
  resetNextInetSocketAddress(result)
  return result
}
```

该方法调用了`resetNextInetSocketAddress(result)`，

###### 「2」RouteSelector.resetNextInetSocketAddress

```java
@Throws(IOException::class)
private fun resetNextInetSocketAddress(proxy: Proxy) {
  // 新建一个InetSocketAddress列表
  val mutableInetSocketAddresses = mutableListOf<InetSocketAddress>()
  inetSocketAddresses = mutableInetSocketAddresses
  // 先声名一个 socketHost(主机名) 和 一个socketPort(端口号)，表示要请求的主机名和端口号
  val socketHost: String
  val socketPort: Int
  if (proxy.type() == Proxy.Type.DIRECT || proxy.type() == Proxy.Type.SOCKS) {
    // 如果是直连代理(也就是无代理)，或者SOCKS代理，那么请求的主机和端口号就是目标服务器的主机号和端口号
    socketHost = address.url.host
    socketPort = address.url.port
  } else {
    // 如果是HTTP代理，那么请求的主机名和端口号应该是HTTP代理服务器的主机名和端口号
    val proxyAddress = proxy.address()
    require(proxyAddress is InetSocketAddress) {
      "Proxy.address() is not an InetSocketAddress: ${proxyAddress.javaClass}"
    }
    socketHost = proxyAddress.socketHost
    socketPort = proxyAddress.port
  }
 
  // 检查端口是否在1 - 65535范围内
  if (socketPort !in 1..65535) {
    throw SocketException("No route to $socketHost:$socketPort; port is out of range")
  }

  
  if (proxy.type() == Proxy.Type.SOCKS) {
    mutableInetSocketAddresses += InetSocketAddress.createUnresolved(socketHost, socketPort)
  } else {
    
    eventListener.dnsStart(call, socketHost)

    // 进行DNS域名解析，这也是一个网络请求，结果会返回 主机名经过DNS解析后返回的IP地址列表，注意是IP地址列表，一个主机名可能可以解析出多个IP
    val addresses = address.dns.lookup(socketHost)
    if (addresses.isEmpty()) {
      throw UnknownHostException("${address.dns} returned no addresses for $socketHost")
    }

    eventListener.dnsEnd(call, socketHost, addresses)

    for (inetAddress in addresses) {
      // 将解析到得InetAddress封装成
      mutableInetSocketAddresses += InetSocketAddress(inetAddress, socketPort)
    }
  }
}
```



##### 【3】RealConnection.connect（待完善）

```java
fun connect(
  connectTimeout: Int,
  readTimeout: Int,
  writeTimeout: Int,
  pingIntervalMillis: Int,
  connectionRetryEnabled: Boolean,
  call: Call,
  eventListener: EventListener
) {
  check(protocol == null) { "already connected" }

  var routeException: RouteException? = null
  val connectionSpecs = route.address.connectionSpecs
  val connectionSpecSelector = ConnectionSpecSelector(connectionSpecs)

  // 如果sslSocketFactory为null，也就是这是一个Http请求
  if (route.address.sslSocketFactory == null) {
    // 如果ConnectionSpec中没有
    if (ConnectionSpec.CLEARTEXT !in connectionSpecs) {
      throw RouteException(UnknownServiceException(
          "CLEARTEXT communication not enabled for client"))
    }
    val host = route.address.url.host
    // 检查目标主机是否允许明文传输
    if (!Platform.get().isCleartextTrafficPermitted(host)) {
      throw RouteException(UnknownServiceException(
          "CLEARTEXT communication to $host not permitted by network security policy"))
    }
  }
  // 如果sslSocketFactory不为null，也就是这是一个Https请求
  else {
    // 如果协议列表包含H2_PRIOR_KNOWLEDGE，抛出异常，H2_PRIOR_KNOWLEDGE是明文Http2协议，而Https不允许明文传输
    if (Protocol.H2_PRIOR_KNOWLEDGE in route.address.protocols) {
      throw RouteException(UnknownServiceException(
          "H2_PRIOR_KNOWLEDGE cannot be used with HTTPS"))
    }
  }

  while (true) {
    try {
      if (route.requiresTunnel()) {
        // 隧道连接
        connectTunnel(connectTimeout, readTimeout, writeTimeout, call, eventListener)
        if (rawSocket == null) {
          // We were unable to connect the tunnel but properly closed down our resources.
          break
        }
      } else {
        // 套接字连接，TCP三次握手在这里进行
        connectSocket(connectTimeout, readTimeout, call, eventListener)
      }
      // 建立协议，TLS四次握手在这里进行
      establishProtocol(connectionSpecSelector, pingIntervalMillis, call, eventListener)
      eventListener.connectEnd(call, route.socketAddress, route.proxy, protocol)
      break
    } catch (e: IOException) {
      socket?.closeQuietly()
      rawSocket?.closeQuietly()
      socket = null
      rawSocket = null
      source = null
      sink = null
      handshake = null
      protocol = null
      http2Connection = null
      allocationLimit = 1

      eventListener.connectFailed(call, route.socketAddress, route.proxy, null, e)

      if (routeException == null) {
        routeException = RouteException(e)
      } else {
        routeException.addConnectException(e)
      }

      if (!connectionRetryEnabled || !connectionSpecSelector.connectionFailed(e)) {
        throw routeException
      }
    }
  }

  if (route.requiresTunnel() && rawSocket == null) {
    throw RouteException(ProtocolException(
        "Too many tunnel connections attempted: $MAX_TUNNEL_ATTEMPTS"))
  }

  idleAtNs = System.nanoTime()
}
```

```java
@Throws(IOException::class)
private fun establishProtocol(
  connectionSpecSelector: ConnectionSpecSelector,
  pingIntervalMillis: Int,
  call: Call,
  eventListener: EventListener
) {
  // 如果不是Https请求（这种情况下sslSocketFactory为null）
  if (route.address.sslSocketFactory == null) {
    // 并且保证支持明文Http2 (H2_PRIOR_KNOWLEDGE)
    if (Protocol.H2_PRIOR_KNOWLEDGE in route.address.protocols) {
      socket = rawSocket
      protocol = Protocol.H2_PRIOR_KNOWLEDGE
      // 启动http2协议
      startHttp2(pingIntervalMillis)
      return
    }

    socket = rawSocket
    protocol = Protocol.HTTP_1_1
    return
  }

  eventListener.secureConnectStart(call)
  // 四次握手过程
  connectTls(connectionSpecSelector)
  eventListener.secureConnectEnd(call, handshake)

  if (protocol === Protocol.HTTP_2) {
    startHttp2(pingIntervalMillis)
  }
}
```

##### 【4】整体流程

介绍完`ExchangeFInder.findConnection`中调用的两个核心方法，我们来看看该方法的整体流程

在介绍流程前，先了解一下多路复用的概念:
多路复用是在Http2.0提出的，也就是只有Http2.0才支持多路复用，多路复用的意思就是一次连接能够并发处理多个请求 ( 详请可见【四.网络技术知识点补充】的【不同http版本的区别】)。所以RealConnection中的calls（call队列）其实也是为Http2.0定制的，因为Http1.0和Http1.1对应的RealConnection没法并发执行多个call

1. 尝试重用call中的conneciton，能重用直接返回该connection，不能重用则将该connection关闭（返回connection的socket，然后关闭该socket）

2. 若无法重用call中的Connection，那么将进行**连接池的第一次查找**:`connectionPool.callAcquirePooledConnection(address, call, null, false)`，第一次调用该方法中只传入了call和adderss，其它两个参数是null和false，(这个时候address还进行做域名解析，所以adderss.url的host字段也就是主机名可能是www.baidu.com这种的，也可能是具体的ip比如192.168.1.0。而连接池中的connection对应的主机名都是具体的ip地址，所以不一定能和address匹配得上)

3. 如果第一次在连接池中没有找到合适的连接，那么就会去寻找一个合适的路由去创建一个新的连接。在这个过程中可能对address进行dns解析，解析后得到一个routes这个时候就会进行**连接池的第二次查找**:`connectionPool.callAcquirePooledConnection(address, call, routes, false)` ，和第一次调用该方法时多传入了routes参数，routes是address经过dns域名解析以后得到的route列表(一个route包含一个IP地址，所以routes也可以看成是IP列表，这里需要注意一个域名可能解析出多个IP地址，详情见【四.网络技术知识点补充】的【1.DNS负载均衡技术】），这个时候再去连接池中寻找是否有连接能够匹配routes中的route即可。

4. 通过寻找到的路由，新建一个RealConnection，并调用其connect方法，connect方法会与服务器主机进行套接字连接(TCP + TLS三次握手)，这个时候会处于阻塞状态。

5. 等到与服务器主机建立完连接后会进行**连接池的第三次查找**:`connectionPool.callAcquirePooledConnection(address, call, routes, true)`，这个时候最后一个参数传入的是true，表示需要将进行多路复用。
   第三次查找的目的是，在建立连接的阻塞过程中，可能有别的请求正好创建了一个适合当前请求的连接并加入了连接池当中。而最后一个参数传入true表示需要进行多路复用，说明第三次查找是为Http2.0定制的。如果当前请求是一个Http2.0的请求，那么在第三次查找中很有可能找到合适的连接并对该连接进行多路复用。（若不是Http2.0的话，可以看成没有第三次查找）

   （1）若在第三次查找中找到合适的连接，那么关闭新建的连接，将call加入到查找到的连接中并返回查找到的连接

   （2）若在第三次查找中仍未找到合适的连接，那么将新建的连接放入到连接池，然后将call加入新建的连接，最后返回新建的连接

# 三 .核心类

### 1.Route

![image-20211122144419815](/Users/shenyutao/Library/Application Support/typora-user-images/image-20211122144419815.png)

它是一个用于描述一条路由的类，主要通过代理服务器信息proxy、连接目标地址InetSocketAddress来描述一条路由。由于代理协议不同，这里的InetSocketAddress会有不同的含义

- 没有代理的情况下它包含的信息是经过了DNS解析的IP以及协议的端口号
- SOCKS代理的情况下，它包含了HTTP服务器的域名和端口号
- HTTP代理的情况下，它包含了代理服务器经过DNS解析后的IP地址和端口号



### 2.Proxy类

![image-20211122145043102](/Users/shenyutao/Library/Application Support/typora-user-images/image-20211122145043102.png)

这是由java原生提供的Proxy类，位于java.net包下
枚举类Type对应上面提到的三种代理类型

- DIRECT ：直连，不使用代理

- HTTP：使用HTTP代理

- SOCKS：使用SOCKS代理

  

### 3.RouterSelector

该类下有一个next方法，通过该方法模拟路由选择的过程，RouteSelector是一个负责管理路由信息，并选择路由的类，它主要有以下三个职责：

1. 收集可用的路由

2. 选择可用的路由

3. 维护连接失败的路由信息

   

### 1.RealConnectionPool

连接池，用于管理当前的连接RealConnect，它在okHttpClient.Builder中创建
![image-20211122104919266](/Users/shenyutao/Library/Application Support/typora-user-images/image-20211122104919266.png)

### 2.RealConnection

RealConnection属性分析

```java
// TCP层的套接字
private var rawSocket: Socket? = null
// 应用层套接字，在rawSocket上分层的SSLSocket，如果此连接不使用SSL，则为rawSocket本身。
private var socket: Socket? = null
  
  ...
  
/*
   如果为true，则无法在此连接上创建新的交换。从池中删除连接时，必须将其设置为true；否则，racing caller可能会在不应该的时候从连接池中得到它。对称地，在从池返回连接之前，必须始终对此进行检查。 

  一旦为true，那么将永远都是true（因为套接字或者资源已经关闭）
*/
var noNewExchanges = false
```

# 四.网络技术知识点补充

### 1.DNS负载均衡技术

一个域名经过DNS解析后得到多个ip，服务器如果做了负载冗余，那么其中一个ip对于的服务器宕机时，另外一个立马补上让用户察觉不到。
冗余：起到的作用是在你的主备服务器的主机宕机时，立刻启动备用机防止不能访问，提供24小时不间断服务。
负载均衡：是在一个DNS服务器中做均衡，比如www.xiaobai.com对应192.168.0.1和192.168.0.2两个IP地址，那么DNS服务器第一次解析www.xiaobai.com域名时返回192.168.0.1，第二次返回192.168.0.2，然后不停循环下去实现负载均衡

重点是需要知道:一个域名可能对应不同的IP地址



### 2.URL统一资源定位符

http://192.168.10.1:80/index.html  这个是带有主机ip和端口的url，ip:端口可以使用域名代替

##### [http://mail.163.com/index.html ](https://link.juejin.cn/?target=http%3A%2F%2Fmail.163.com%2Findex.html)

- 1、http:// -----> 这个是协议, HTTP超文本传输协议, 也就是网页在网上传输的协议.
- 2、mail -----> 这个是服务器类型，用于决定解析该域名的DNS服务器, mail代表由邮箱服务器来解析该域名
- 3、163.com -----> 这个是域名, 是用来定位网站的独一无二的名字.
- 4、mail.163.com ->这个是主机名(网站名), 由服务器名 + 域名组成.
- 5、/ -----> 这个是根目录, 也就是说, 通过网站名找到服务器, 然后在服务器存放网页的根目录.
- 6、[mail.163.com/index.html](https://link.juejin.cn/?target=http%3A%2F%2Fmail.163.com%2Findex.html) -----> url, 统一资源定位符, 全球性地址, 用于定位网上的资源.



### 3.代理服务器

代理服务器按照协议可以分为Http，Https，Socks，FTP代理服务器等

代理服务器还分为正向代理和反向代理，其中：

- 正向代理：正向代理的代理对象是客户端。客户端访问外网的时候，不直接请求目标服务器(比如位于外网的Web服务器)，而是先向代理服务器发送请求，让代理服务器去访问目标服务器，代理服务器从目标服务器上得到响应以后再讲响应转发给发出请求的客户端。
  ![image-20211123164826560](/Users/shenyutao/Library/Application Support/typora-user-images/image-20211123164826560.png)

- 逆向代理：逆向代理的代理对象是服务器。当客户端向目标服务器发送请求的时候，不是直接发送给目标服务器，而是先发送给目标服务器的代理服务器，再由代理服务器转发给目标服务器，待目标服务器做出响应后，代理服务器再将响应转发给发出请求的客户端
  ![image-20211123164957787](/Users/shenyutao/Library/Application Support/typora-user-images/image-20211123164957787.png)

  

  总的来说，
  正向代理的对象是客户端，它能够隐藏客户端的IP

  逆向代理的代理对象是服务器，它能够隐藏服务器的IP

  

### 4.不同http版本的区别

Http1：一次请求-响应，建立一个连接，用完就关闭；每一次请求都要建立一个连接

Http1.1：一次连接可用处理多个请求，若干个请求排队串行单线程处理，后面的请求等待前面的请求返回后才开始执行，一旦某个请求超时，后续请求只能被阻塞，也就是线头阻塞。

Http2 ：多个请可以同时在一个连接上并行执行。某个任务耗时严重，不会影响到其他连接的正常执行



### 5.连接重用

参考文档链接：https://daniel.haxx.se/blog/2016/08/18/http2-connection-coalescing/

#### 链接文档核心内容：

假设这个很酷的虚构站点“example.com”在 DNS 中有两个名称条目：A.example.com 和 B.example.com。当通过 DNS 解析这些名称时，客户端会为每个名称获取一个 IP 地址列表。一个很可能包含 IPv4 和 IPv6 地址混合的列表。每个名称一个列表。

您还必须记住，HTTP/2 也只被浏览器通过 HTTPS 使用，因此对于每个使用 HTTP/2 的源，还有一个相应的服务器证书，其中包含名称列表或通配符模式，该服务器有权响应.

在我们的示例中，我们首先将浏览器连接到 A。假设解析 A 从 DNS 返回 IP 192.168.0.1 和 192.168.0.2，因此浏览器继续连接到这些地址中的第一个，即以“1”结尾的地址”。浏览器在 TLS 握手中获取服务器证书，因此，它还获取服务器可以处理的主机名列表：A.example.com 和 B.example.com。（它也可以是像“*.example.com”这样的通配符）

如果浏览器随后想要连接到 B，它也会将该主机名解析为 IP 列表。假设这里是 192.168.0.2 和 192.168.0.3。

```
主机 A：192.168.0.1 和 192.168.0.2
主机 B：192.168.0.2 和 192.168.0.3
```

#### 火狐的连接重用

主机 A 有两个地址，主机 B 有两个地址。地址列表并不相同，但**存在重叠**——两个列表都包含 192.168.0.2。并且主持人A已经声明它对B也具有权威性。在这种情况下，Firefox 不会再次连接到主机 B。它将重用到主机 A 的连接，并通过该*单个共享连接*请求主机 B 的内容。这是使用中最激进的合并方法。

![一个连接](https://daniel.haxx.se/blog/wp-content/uploads/2016/08/connection-coalescing3-1.jpg)

#### Chrome的连接重用

Chrome 具有稍微不那么激进的合并。在上面的示例中，当浏览器连接到第一个主机名的 192.168.0.1 时，Chrome 将要求主机 B 的 IP 包含该特定 IP，以便它重用该连接。如果主机 B 返回的 IP 确实是 192.168.0.2 和 192.168.0.3，则它显然不包含 192.168.0.1，因此 Chrome 将创建到主机 B 的新连接。

如果解析主机 B 返回一个包含连接主机 A 已使用的特定 IP 的列表，Chrome 将重用与主机 A 的连接。 



### 6.Https握手过程

Http: 直接使用明文在客户端和服务器之间完成数据通讯
Https: 采用非对称加密和对称加密相结合的方式加密传输的内容，保证客户端和服务器之间的通信安全。
（非对称算法用于加密交换秘钥 + 对称算法用于加密传输数据 +数字证书验证身份）

这里讲一下对称加密和非对称加密的一个区别：
对称加密：明文P通过密钥K加密后得到密文M，密文M能够通过秘钥P解密成明文P
非对称加密：有一组密钥即公钥和私钥，公钥加密明文P得到密文M，密文M无法通过公钥解密成明文P，只能通过私钥解密



#### Https：

###### Tcp三次握手过程：

1. (SYN = 1,Seq = x)客户端向服务端发送一个SYN标志位为1以及Seq = x的请求，表示请求和服务端建立连接
2. (SYN=1,ACK=1,Seq =y,ACKNum = x + 1)服务端收到请求后发回确认应答，发送一个SYN和ACK标志位均为1，Seq为y的报文，发送完毕后，服务端进入SYN _RCVD状态
3. (ACK = 1 ,ACKNum = y+1)客户端验证ACKNum即ACKNum是否等于x + 1，然后发送一个ACKNum = y+1的报文
4. 发送完毕后，客户端进入 ESTABLISHED 状态，当服务端接到这个包时，也进入 ESTABLISHED 状态，至此 TCP 握手结束。

###### TLS握手过程：

1. 首先，客户端向服务器发起握手请求，以明文传输请求信息，主要包括版本号、客户端支持的加密算法、客户端支持的压缩算法等
2. 服务器的配置：支持Https协议的服务器必须要有一套数字证书，可以自己制作也可以向组织申请。自己颁发的证书需要客户端验证通过才可以继续访问，而使用向受信任的公司申请的证书则不会弹出提示页面。
3. 服务端收到请求后，从客户端支持的加密算法中选择一种(服务器也支持该加密算法)并连同证书返回给客户端(公钥就包含在证书当中，证书中还包含颁发机构，过期时间等信息)
4. 客户端验证证书的合法性，包括是否可信，是否过期，以及服务器支持的域名列表等
5. 客户端随机生成一个对称加密算法的共享密钥（这里也叫对称秘钥），并通过证书中的公钥进行加密后发送给服务器
6. 服务器收到后利用私钥解密，得到共享密钥后向客户端发送Finish消息，完成握手过程

######    ![image-20211129102221797](/Users/shenyutao/Library/Application Support/typora-user-images/image-20211129102221797.png)

###### 开始通信：

1. 服务器利用对称密钥加密客户端想要的数据，并发送给客户端
2. 客户端收到数据后用对称秘钥解密，得到明文数据

###### 四次挥手：

   Seq连接序列号，就是用于确认发送报文的对象

1. (FIN = 1,Seq = x) 客户端向服务器发送一个FIN标志位为1，连接序号为x的请求用来关闭客户端到服务器的数据传送，此时Client进入FIN_WAIT_1状态

2. (ACK = 1 ，ACKNum = x +1) 收到FIN请求后，服务端向Client发送一个ACK = 1 ，ACKnum = x + 1 的报文确认关闭客户端到服务器的数据传送，此时服务端进入CLOSE_WAIT状态

   注意：这个时候只是关闭了客户端到服务端之间的数据传输，客户端以及不能够再想服务器发送数据。而服务端到客户端之间的数据传输还未关闭，服务端此时仍可向客户端发送数据

3. (FIN = 1,Seq = y) 服务端向客户端发送一个FIN报文用来关闭服务端到客户端的数据传输，服务端进入LAST_ACK状态

4. (ACK = 1,Seq = y + 1)Client收到FIN后，发送一个ACK给服务端，服务端确认验证Seq后进入CLOSED状态完成四次挥手

