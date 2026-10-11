package com.fittrace.app;

import android.os.Bundle;
import android.view.View;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bridge.getWebView().setVerticalScrollBarEnabled(false);
        bridge.getWebView().setHorizontalScrollBarEnabled(false);
        bridge.getWebView().setOverScrollMode(View.OVER_SCROLL_NEVER);
    }
}
