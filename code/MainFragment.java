package cn.eshifu.mclient.ui;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.tapadoo.alerter.Alerter;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import butterknife.ButterKnife;
import cn.eshifu.mclient.R;
import cn.eshifu.mclient.app.IntentAction;
import cn.eshifu.mclient.app.IntentExtras;
import cn.eshifu.mclient.event.FragmentShowHideEvent;
import cn.eshifu.mclient.event.StartBrotherEvent;
import cn.eshifu.mclient.event.TabSelectEvent;
import cn.eshifu.mclient.ui.my.MyTabFragment;
import cn.eshifu.mclient.ui.order.OrderTabFragment;
import cn.eshifu.mclient.ui.order.family.FamilyOrderSettleFragment;
import cn.eshifu.mclient.ui.order.family.detail.FamilyOrderDetailToPaidFragment;
import cn.eshifu.mclient.ui.order.family.detail.FamilyOrderDetailToSettleFragment;
import cn.eshifu.mclient.ui.transaction.TransactionTabFragment;
import cn.eshifu.mclient.ui.workbench.WorkbenchFragment;
import cn.eshifu.mclient.util.Logger;
import cn.eshifu.mclient.util.StringUtils;
import cn.eshifu.mclient.view.BottomBar;
import cn.eshifu.mclient.view.BottomBarTab;
import me.yokeyword.fragmentation.SupportFragment;

public class MainFragment extends BaseFragment {
    private static final String TAG = MainFragment.class.getSimpleName();

    public static final int FIRST = 0;
    public static final int SECOND = 1;
    public static final int THIRD = 2;
    public static final int FOURTH = 3;
    public static final String FIRST_TAB_TEXT = "工作台";
    public static final String SECOND_TAB_TEXT = "订单";
    public static final String THIRD_TAB_TEXT = "收支";
    public static final String FOURTH_TAB_TEXT = "我的";

    private BottomBar mBottomBar;
    private SupportFragment[] mFragments = new SupportFragment[4];

    public static MainFragment newInstance() {
        Bundle args = new Bundle();
        MainFragment fragment = new MainFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        ButterKnife.bind(this, view);

        if (savedInstanceState == null) {
            mFragments[FIRST] = WorkbenchFragment.newInstance();
            mFragments[SECOND] = OrderTabFragment.newInstance();
            mFragments[THIRD] = TransactionTabFragment.newInstance();
            mFragments[FOURTH] = MyTabFragment.newInstance();

            loadMultipleRootFragment(R.id.fl_tab_container, FIRST,
                                    mFragments[FIRST],
                                    mFragments[SECOND],
                                    mFragments[THIRD],
                                    mFragments[FOURTH]);
        } else {
            mFragments[FIRST] = findChildFragment(WorkbenchFragment.class);
            mFragments[SECOND] = findChildFragment(OrderTabFragment.class);
            mFragments[THIRD] = findChildFragment(TransactionTabFragment.class);
            mFragments[FOURTH] = findChildFragment(MyTabFragment.class);
        }

        initView(view);

        return view;
    }

    private void initView(View view) {
        mBottomBar = (BottomBar) view.findViewById(R.id.bottomBar);

        mBottomBar
                .addItem(new BottomBarTab(_mActivity, R.drawable.ic_tab_workbench, FIRST_TAB_TEXT))
                .addItem(new BottomBarTab(_mActivity, R.drawable.ic_tab_order, SECOND_TAB_TEXT))
                .addItem(new BottomBarTab(_mActivity, R.drawable.ic_tab_trade, THIRD_TAB_TEXT))
                .addItem(new BottomBarTab(_mActivity, R.drawable.ic_tab_my, FOURTH_TAB_TEXT));

        mBottomBar.setOnTabSelectedListener(new BottomBar.OnTabSelectedListener() {
            @Override
            public void onTabSelected(int position, int prePosition) {
                showHideFragment(position, prePosition);
            }

            @Override
            public void onTabUnselected(int position) {

            }

            @Override
            public void onTabReselected(int position) {
                EventBus.getDefault().post(new TabSelectEvent(position));
            }
        });
    }

    public void showHideFragment(int position, int prePosition) {
        if (position == prePosition) {
            return ;
        }

        showHideFragment(mFragments[position], mFragments[prePosition]);
    }

    /**
     * start other BrotherFragment
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void startBrother(StartBrotherEvent event) {
        if (event != null && event.targetFragment != null) {
            start(event.targetFragment);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void showHideFragment(FragmentShowHideEvent event) {
        if (event != null) {
            mBottomBar.setCurrentItem(event.descPosition);
        }
    }
}