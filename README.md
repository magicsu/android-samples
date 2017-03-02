##介绍
关于RxJava、Retrofit、Realm、单Activity多Fragment、多进程等的实践。项目code中有实际项目使用的部分源码。

![screenshot00](https://github.com/magicsu/android-samples/blob/master/screenshot/Intro.jpg?raw=true)

![screenshot00](https://github.com/magicsu/android-samples/blob/master/screenshot/Intro.jpg?raw=true)

##前言
很纠结要不要写这样一篇介绍的文章，因为现在网上有很多关于这方面的文章，但是关于这几个开源项目一起搭配使用，在实际项目中的使用的文章还是很少。正好，最近有个东西用到了介绍里所说的这些开源项目，对于我来说，很多也是第一次用，作为一个新司机（第一次写技术文章~），还是想实习上路，写一下自己在项目中的实际使用，让更多的人使用到这些开源项目。希望可以给刚入门的你一丝温暖。

##RxJava

学习了RxJava，懂得了很多道理，但依旧不会用？
让我们先来举几个实际常用的例子。

###线程切换
举个例子，我们想从服务端获取一个图片链接(耗时操作)，然后使用该链接生成一个二维码图片（耗时操作），然后想在UI主线程显示出来。

```
RequestManager.getApiManager()
        .getQRCodeUrl()                // 网络获取要生成的二维码内容（模拟耗时操作）
        .subscribeOn(Schedulers.io())  // 指定被观察者执行线程
        .filter(new Func1<StringResponse, Boolean>() {
            @Override
            public Boolean call(StringResponse response) {
                return response.isSuccess() && response.getData() != null;
            }
        })
        .map(new Func1<StringResponse, String>() {
            @Override
            public String call(StringResponse response) {
                return response.getData();
            }
        })
        .filter(new Func1<String, Boolean>() {
            @Override
            public Boolean call(String s) {
                return StringUtils.isNotEmpty(s);
            }
        })
        .observeOn(Schedulers.io())    // 将接下来的执行环境切换为IO线程
        .map(new Func1<String, Bitmap>() {
            @Override
            public Bitmap call(String s) {
            	 // 生成二维码图片（模拟耗时操作）
                return ZXingUtils.encodeQRCodeImage(s, 500, 500);
            }
        })
        .filter(new Func1<Bitmap, Boolean>() {
            @Override
            public Boolean call(Bitmap bitmap) {
                return bitmap != null;
            }
        })
        .observeOn(AndroidSchedulers.mainThread())  // 将接下来的执行环境切换为主线程
        .subscribe(new Subscriber<Bitmap>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onNext(Bitmap bitmap) {
            	 // 切换为主线程显示图片
                mIvQRCode.setImageBitmap(bitmap);
            }
        });
```

由上面可以看到，我们通过使用操作符将事件分解，通过subscribeOn（）和observeOn（）方法切换代码执行的线程环境。对于线程切换，我们只需要掌握以下两个方法：

* subscribeOn（）它指示Observable在一个指定的调度器上创建（只作用于被观察者创建阶段）。只能指定一次，如果指定多次则以第一次为准

* observeOn（）指定在事件传递（加工变换）和最终被处理（观察者）的发生在哪一个调度器。可指定多次，每次指定完都在下一步生效。

###按钮防抖动
这个小功能平时在很多地方都有用到，可以有效防止短时间内多次触发造成的异常情况。如果需要RxJava实现这个功能，需要使用RxBinding将点击事件转化为一个Observable，接着，使用 throttleFirst 操作符来解决按钮被多次点击的问题。throttleFirst允许设置一个时间长度，之后它会发送固定时间长度内的第一个事件，而屏蔽其它事件，在间隔达到设置的时间后，可以再发送下一个事件，如下图所示。

![throttleFirst](http://img1.tuicool.com/uAjQ7fm.png!web)

实例代码如下：

```
RxView.clicks(mBtnLogIn)
        .throttleFirst(500, TimeUnit.MILLISECONDS)  // 每500ms内取第一次点击事件
        .subscribe(new Action1<Void>() {
            @Override
            public void call(Void aVoid) {
                
            }
        });
```
注意：在使用时，切记，在页面创建时执行以上代码完成事件绑定，而非将以上代码放入按钮监听回调中，否则会出现第一次触发时连续点击按钮两次才会有响应的"奇怪现象"。

###多数据源加载数据处理
在实际的项目中，经常会碰到一处数据可能要从多数据源加载的情况。比如一个列表数据，可能会先从内存缓存中尝试加载，如果没有或者过期，接着从本地数据库中加载，如果还没有有效数据，需要从网络获取。

我们可以看下如下使用示例：

```
Observable<Data> memory = ...;   
Observable<Data> disk = ...;   
Observable<Data> network = ...; 
 
Observable<Data> source = Observable   
		  .concat(memory, disk, network) 
		  .first(); 
```

我们可以按照以上方式完成一个简单的多数据源获取数据代码，但是

下面将展示一个实际使用例子示例代码，有一个订单列表，多数情况下只需要加载缓存数据，偶尔可能需要刷新缓存数据，列表中的某个字段可能会有一些更新，可以根据需要设置是否需要获取缓存数据。完整代码见[todo]。

```
private Map<String, FamilyOrder> mCachedOrders;
private boolean mCacheIsDirty;

// 获取订单列表数据
@Override
public Observable<List<FamilyOrder>> getOrders(int pageNum, int pageSize) {
    if (mCachedOrders != null && !mCacheIsDirty) {
        Logger.d(TAG, "<<<< mCachedOrders != null && !mCacheIsDirty <<<<");
        return Observable.from(mCachedOrders.values()).toList();
    } else {
        mCachedOrders = new HashMap<>();
    }

    Observable<List<FamilyOrder>> localOrders = mLocalDataSource.getOrders(pageNum, pageSize);

    Observable<List<FamilyOrder>> remoteOrders = mRemoteDataSource
            .getOrders(pageNum, pageSize, isWaitPay) // 注意，这里的线程操作环境已经为IO线程
            .filter(new Func1<List<FamilyOrder>, Boolean>() {
                @Override
                public Boolean call(List<FamilyOrder> familyOrders) {
                    return familyOrders != null;
                }
            })
            .doOnNext(new Action1<List<FamilyOrder>>() {
                @Override
                public void call(List<FamilyOrder> familyOrders) {
                    // 数据库缓存
                    saveOrUpdateFamilyOrders(familyOrders);
                }
            })
            .flatMap(new Func1<List<FamilyOrder>, Observable<FamilyOrder>>() {
                @Override
                public Observable<FamilyOrder> call(List<FamilyOrder> familyOrders) {
                		// 将普通数据集合遍历包装成单独的Observable
                    return Observable.from(familyOrders);
                }
            })
            .doOnNext(new Action1<FamilyOrder>() {
                @Override
                public void call(FamilyOrder familyOrder) {
                    // 内存缓存
                    mCachedOrders.put(familyOrder.getOrderId(), familyOrder);
                }
            })
            .toList()
            .doOnCompleted(new Action0() {
                @Override
                public void call() {
                    mCacheIsDirty = false;
                }
            });

    if (mCacheIsDirty) {
        return remoteOrders;
    } else {
        return Observable.concat(localOrders, remoteOrders)
        	  .first(new Func1<List<FamilyOrder>, Boolean>() {
	             @Override
	             public Boolean call(List<FamilyOrder> familyOrders) {
	                 return familyOrders != null;  // 亦可在这里判断数据是否过期、有效
	             }
	         });
    }
}

// 完整代码详见[todo]
```

使用这种模式的关键在与理解concat操作符，需要知道concat只有需要数据的时候才会订阅所有的Observable数据源，而且所有的数据源是顺序串联的队列，以concat(localOrders, remoteOrders）为例，如果localOrders存在有效数据，remoteOrders就不会被访问，因为first()/takeFirst()操作符会提前停止队列，若没有，则会依次执行下面的数据源。

另外，建议在使用时根据需要考虑将某些数据操作“统筹处理”，比如封装一个DataReposity，实现数据操作接口，将多重数据源操作封装到一起。详情代码[todo]。

##Retrofit

实际项目使用时，我们可以将Retrofit封装一下，使用单例模式调用。除了要设置基础的网络的相应的超时时间、baseUrl、使用的序列化库的ConverterFactory。结合RxJava使用还要设置CallAdapterFactory。

###拦截器
很多应用都有这种需求，对于客户端请求进行Token校验，如果token失效可能需要进行其他操作，如重新登录等。此时，使用Retrofit可以很方便的对所有网络请求添加拦截器，在拦截器中通过覆写intercept方法可以很方便的做进一步处理。

###使用示例
另外，Gson和Realm使用时，很可能会遇到Gson在对RealmObject对象操作时出现循环引用的异常，造成堆栈溢出，在对Retrofit配置ConverterFactory时需要先对Gson设置setExclusionStrategies覆写shouldSkipField方法。

详细使用可参考如下代码。

```
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

        // 日志(可根据不同的环境选择不同的日志输出，方便调试)
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
                        // 跳过RealmObject、Drawable造成的循环引用异常
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


public interface ApiManager {

    @GET("app/checkUpdate")
    Observable<CheckUpdateResponse> checkUpdate();
    
}

```

我们可以通过如下方法进行调用。

```
RequestManager.getApiManager()
    .checkUpdate()
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(new Subscriber<CheckUpdateResponse>() {
        @Override
        public void onCompleted() {
        }

        @Override
        public void onError(Throwable e) {
        }

        @Override
        public void onNext(final CheckUpdateResponse response) {
            // do something
        }
    }
}
```

##Realm

对于Realm的使用，官方有非常详细的[文档](https://realm.io/docs/java/latest/)。

对于为什么要使用Realm，除了对RxJava的先天支持（可能仅仅这一条你就毫无抵抗地爱上了她），另外Realm查询速度灰常快，内存占用低，API使用简单等等，都会让你爱不释手。

但是，Realm也有自身的限制，比如在多线程环境下使用Realm数据，对于第一次使用的人来说，会有一脸懵逼的感觉。但是官方文档在这方面做得十分友好，既然问题不可避免，那就教会大家如何正确使用。关于多线程使用，这个有个[传送门](https://github.com/realm/realm-java/tree/master/examples/threadExample)。在使用的过程中一定要多加注意。另外切记注意Relam的close，避免造成内存泄露。

###使用示例
这是一个官方示例，和RxJava搭配使用。Realm从本地数据库中异步查询本地用户，然后将查询数据通过flatMap操作符依次查询用户的Github信息，将最终查询信息展示。

```
Realm realm = Realm.getDefaultInstance();
GitHubService api = retrofit.create(GitHubService.class);
realm.where(Person.class).isNotNull("username").findAllAsync().asObservable()
    .filter(persons.isLoaded)
    .flatMap(persons -> Observable.from(persons))
    .flatMap(person -> api.user(person.getGithubUserName())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(user -> showUser(user));
   
```

###调试
对于Realm的调试，我们可以使用官方的数据库管理软件，RealmBrowser，用来查看数据表。

对于新手来说，第一件事就是如何获取Realm文件。
我们可以通过如下代码将Realm文件copy到手机中，然后通过adb push命令将文件传到电脑中，使用RealmBrowser查看。

```
public static void copyRealmFile2SDCard() {
    Realm realm = null;
    try {
        realm = Realm.getDefaultInstance();
        File f = new File(realm.getPath());
        if (f.exists()) {
            try {
                // 将文件拷贝到手机指定位置
                FileUtils.copy(f, new File(Environment.getExternalStorageDirectory() + File.separator + "default.realm"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    } finally {
        if (realm != null)
            realm.close();
    }
}

```


##生命周期管理
写到这里，一定要提醒一点的是RxJava生命周期的管理。不管是使用Retrofit进行网络请求，还是通过Realm获取本地数据，都可以将返回的Subscription加入到CompositeSubscription中进行管理，使用unsubscribe()可以及时有效的停止接收事件，避免内存泄露异常。

我们可以在BaseActivity/BaseFragment中进行如下处理。可参考如下代码。

```
public BaseFragment extends Fragment {
	protected CompositeSubscription mSubscriptions = new CompositeSubscription();
		
	@Override
	public void onDestroy() {
	    super.onDestroy();
	    if (mSubscriptions != null && !mSubscriptions.isUnsubscribed()) {
	        // 释放资源
	        mSubscriptions.unsubscribe();
	    }
	}
	
	....
}

public DemoFragment extends BaseFragment {

	....
	private void checkUpdate() {
		 // 每个subscribe 函数都会返回一个Subscription
		 // 添加到CompositeSubscription管理
		 mSubscriptions.add(RequestManager.getApiManager()
                .checkUpdate()
                ...
                ));
	}
}


```

##TODO
* 单Activity多Fragment的使用及注意项
* Realm、Retrofit、Realm使用细化

##写在最后

###实施成果与感受
这几个开源项目搭配起来使用，是为了解决之前旧项目的一些问题。比如程序内存占用高、性能低，代码耦合严重、存在大量模板代码、以及程序稳定性差。了解分析后，决定参照[googlesamples/android-architecture](https://github.com/googlesamples/android-architecture/)的todo‑mvp‑rxjava项目，结合retrofit、realm、eventbus的使用，同时，为了提升页面的流畅度，决定使用[单activity多fragment]的UI框架，开发完成后，实际使用效果很流畅，主要体现在网络请求、数据库操作、页面切换流畅度方面，内存占用也比较满意。

###关于保活
另外，提一下程序保活，由于这个app需要常驻后台，这个关系到用户的切身利益，由于使用群体的特殊性，对保活要求更高。网上也要很多的保活措施，但是很多对于系统版本兼容、耗电等情况不尽人意。结合实践，觉得有两点很重要。一是多进程的使用，将后台服务模块与UI模块分离，保证后台服务进程较低的内存占用，二是，将后台服务进程进程提升为前台服务，提升进程优先级。还可以通过将应用退出后，主动kill掉UI进程，来降低手机内存占用，进一步降低服务进程被kill概率。另外，还可以采用“1像素activity”的方式去提升进程优先级，记得将activity的进程和要保活的进程设置为同一个process。另外，也希望大家采取克制的态度去使用各式各样的保活机制，很多保活机制对手机的电量、流量都有一定的影响，是的，我们要克制的达成目标。
