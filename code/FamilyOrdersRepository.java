package cn.eshifu.mclient.data.source;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import cn.eshifu.mclient.data.entity.FamilyOrder;
import cn.eshifu.mclient.data.source.local.FamilyOrdersLocalDataSource;
import cn.eshifu.mclient.data.source.remote.FamilyOrdersRemoteDataSource;
import cn.eshifu.mclient.util.Logger;
import cn.eshifu.mclient.util.StringUtils;
import io.realm.Realm;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;

public class FamilyOrdersRepository implements FamilyOrderDateSource {
    private static final String TAG = FamilyOrdersRepository.class.getSimpleName();

    private volatile static FamilyOrdersRepository INSTANCE = null;

    private final FamilyOrdersLocalDataSource mLocalDataSource;
    private final FamilyOrdersRemoteDataSource mRemoteDataSource;

    private Map<String, FamilyOrder> mCachedOrders;
    private boolean mCacheIsDirty;

    public FamilyOrdersRepository(FamilyOrdersLocalDataSource familyOrdersLocalDataSource,
                                  FamilyOrdersRemoteDataSource familyOrdersRemoteDataSource) {
        mLocalDataSource = familyOrdersLocalDataSource;
        mRemoteDataSource = familyOrdersRemoteDataSource;
    }

    public static FamilyOrdersRepository getInstance(FamilyOrdersLocalDataSource familyOrdersLocalDataSource,
                                                     FamilyOrdersRemoteDataSource familyOrdersRemoteDataSource) {
        if (INSTANCE == null) {
            synchronized (FamilyOrdersRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new FamilyOrdersRepository(familyOrdersLocalDataSource, familyOrdersRemoteDataSource);
                }
            }
        }
        return INSTANCE;
    }

    @Override
    public Observable<List<FamilyOrder>> getOrders(int pageNum, int pageSize, boolean isWaitPay) {
        if (mCachedOrders != null && !mCacheIsDirty) {
            return Observable.from(mCachedOrders.values()).toList();
        } else {
            mCachedOrders = new HashMap<>();
        }

        Observable<List<FamilyOrder>> localOrders = mLocalDataSource.getOrders(pageNum, pageSize, isWaitPay);

        Observable<List<FamilyOrder>> remoteOrders = mRemoteDataSource
                .getOrders(pageNum, pageSize, isWaitPay)
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
                  .takeFirst(new Func1<List<FamilyOrder>, Boolean>() {
                @Override
                public Boolean call(List<FamilyOrder> familyOrders) {
                    return familyOrders != null;
                }
            });
        }
    }

    @Override
    public Observable<FamilyOrder> getOrder(String orderId) {
        if (StringUtils.isEmpty(orderId)) {
            return null;
        }

        FamilyOrder familyOrder = getOrderById(orderId);

        if (familyOrder != null) {
            return Observable.just(familyOrder);
        }

        Observable<FamilyOrder> localOrder = mLocalDataSource.getOrder(orderId);
        Observable<FamilyOrder> remoteOrder = mRemoteDataSource.getOrder(orderId);

        return Observable.concat(localOrder, remoteOrder).first(new Func1<FamilyOrder, Boolean>() {
                    @Override
                    public Boolean call(FamilyOrder familyOrder) {
                        return familyOrder != null;
                    }
                });
    }

    @Override
    public void confirmOrder(FamilyOrder familyOrder) {

    }

    @Override
    public void confirmOrder(String orderId) {

    }

    @Override
    public void saveOrder(FamilyOrder familyOrder) {

    }

    @Override
    public void updateOrder(FamilyOrder familyOrder) {
        mCachedOrders.put(familyOrder.getOrderId(), familyOrder);
    }

    @Override
    public void refreshOrders() {
        mCacheIsDirty = true;
    }

    private FamilyOrder getOrderById(String orderId) {
        if (mCachedOrders == null || mCachedOrders.isEmpty()) {
            return null;
        } else {
            return mCachedOrders.get(orderId);
        }
    }

    private void saveOrUpdateFamilyOrders(final List<FamilyOrder> orders) {
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                realm.copyToRealmOrUpdate(orders);
            }
        });
        realm.close();
    }
}
