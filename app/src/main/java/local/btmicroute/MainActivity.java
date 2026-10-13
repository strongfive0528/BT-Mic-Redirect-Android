package local.btmicroute;

import android.app.Activity;
import android.graphics.Color;
import android.media.*;
import android.os.*;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import java.util.List;

/** 只维持通信路由，绝不创建 AudioRecord 或请求 RECORD_AUDIO。 */
public final class MainActivity extends Activity {
    private static final String TAG = "BtMicRoute", BLE_HEADSET = "蓝牙 LE 耳机";
    private static final int TYPE_BLE_HEADSET = 26;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audio; private TextView stateTitle, stateDetail, diagnostics;
    private boolean requested, silenceRunning; private AudioTrack silenceTrack; private Thread silenceThread;
    private int attempts;
    private final Runnable routeAttempt = this::applyRoute;
    private final Runnable refreshTask = new Runnable() { public void run() { refresh(); handler.postDelayed(this, 1000); } };
    private final AudioDeviceCallback devices = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] d) { refresh(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] d) { refresh(); }
    };

    @Override public void onCreate(Bundle state) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(state);
        audio = getSystemService(AudioManager.class); audio.registerAudioDeviceCallback(devices, handler);
        setContentView(content()); refresh();
    }

    private View content() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout page = column(20); page.setPadding(dp(20), dp(28), dp(20), dp(28)); scroll.addView(page);
        page.addView(text("音频工具", 14, c(com.google.android.material.R.attr.colorPrimary)));
        TextView title = text("蓝牙麦克风路由", 30, c(com.google.android.material.R.attr.colorOnSurface)); title.setPadding(0, dp(4), 0, 0); page.addView(title);
        TextView sub = text("为 WO Mic 保持蓝牙耳机的通话麦克风通道", 16, c(com.google.android.material.R.attr.colorOnSurfaceVariant)); sub.setPadding(0, dp(6), 0, dp(20)); page.addView(sub);

        MaterialCardView state = card(c(com.google.android.material.R.attr.colorSurfaceContainerLow)); LinearLayout box = column(20);
        stateTitle = text("正在检查蓝牙音频…", 20, c(com.google.android.material.R.attr.colorOnSurface));
        stateDetail = text("请先连接耳机，然后启用路由。", 14, c(com.google.android.material.R.attr.colorOnSurfaceVariant)); stateDetail.setPadding(0, dp(6), 0, 0);
        box.addView(stateTitle); box.addView(stateDetail); state.addView(box); page.addView(state, lp(16));

        MaterialButton enable = new MaterialButton(this); enable.setText("启用蓝牙麦克风路由"); enable.setTextSize(16); enable.setIconResource(android.R.drawable.ic_media_play); enable.setOnClickListener(v -> requestRoute()); page.addView(enable, lp(10));
        MaterialButton disable = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle); disable.setText("停止并清除路由"); disable.setIconResource(android.R.drawable.ic_menu_close_clear_cancel); disable.setOnClickListener(v -> clearRoute()); page.addView(disable, lp(20));

        MaterialCardView reconnect = card(c(com.google.android.material.R.attr.colorErrorContainer)); LinearLayout warn = column(18);
        warn.addView(text("没有生效？请重新连接蓝牙设备", 17, c(com.google.android.material.R.attr.colorOnErrorContainer)));
        TextView advice = text("如果没有显示“蓝牙 SCO 已就绪”，请断开耳机并重新连接，确认已打开“通话音频”，再点击启用。仅媒体音频（A2DP）不能使用耳机麦克风。", 14, c(com.google.android.material.R.attr.colorOnErrorContainer)); advice.setPadding(0, dp(6), 0, 0); warn.addView(advice); reconnect.addView(warn); page.addView(reconnect, lp(20));
        page.addView(text("连接详情", 16, c(com.google.android.material.R.attr.colorOnSurface)), lp(8));
        MaterialCardView details = card(c(com.google.android.material.R.attr.colorSurfaceContainerLow)); diagnostics = text("", 13, c(com.google.android.material.R.attr.colorOnSurfaceVariant)); diagnostics.setTextIsSelectable(true); diagnostics.setTypeface(android.graphics.Typeface.MONOSPACE); details.addView(diagnostics, new MaterialCardView.LayoutParams(-1, -2) {{ setMargins(dp(16), dp(16), dp(16), dp(16)); }}); page.addView(details);
        return scroll;
    }
    private MaterialCardView card(int color) { MaterialCardView v = new MaterialCardView(this); v.setCardBackgroundColor(color); v.setCardElevation(0); v.setRadius(dp(24)); return v; }
    private LinearLayout column(int padding) { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(padding, padding, padding, padding); return v; }
    private TextView text(String s, float size, int color) { TextView v = new TextView(this); v.setText(s); v.setTextSize(TypedValue.COMPLEX_UNIT_SP, size); v.setTextColor(color); v.setLineSpacing(0, 1.15f); return v; }
    private LinearLayout.LayoutParams lp(int bottom) { LinearLayout.LayoutParams v = new LinearLayout.LayoutParams(-1, -2); v.bottomMargin = dp(bottom); return v; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int c(int attr) { return MaterialColors.getColor(this, attr, Color.MAGENTA); }

    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(refreshTask); handler.post(refreshTask); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(refreshTask); }
    @Override protected void onDestroy() { handler.removeCallbacks(refreshTask); handler.removeCallbacks(routeAttempt); audio.unregisterAudioDeviceCallback(devices); if (isFinishing()) clearRoute(); super.onDestroy(); }

    private void requestRoute() {
        handler.removeCallbacks(routeAttempt); attempts = 20;
        try { audio.setMode(AudioManager.MODE_IN_COMMUNICATION); startSilence(); audio.startBluetoothSco(); applyRoute(); }
        catch (RuntimeException error) { requested = false; stopSilence(); Log.e(TAG, "无法开始路由", error); refresh(); }
    }
    @SuppressWarnings("deprecation") private void applyRoute() {
        AudioDeviceInfo target = null;
        for (AudioDeviceInfo d : audio.getAvailableCommunicationDevices()) if (d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || d.getType() == TYPE_BLE_HEADSET) { target = d; break; }
        if (target == null) { if (--attempts > 0) handler.postDelayed(routeAttempt, 500); else { audio.setMode(AudioManager.MODE_NORMAL); stopSilence(); } refresh(); return; }
        try { requested = audio.setCommunicationDevice(target); if (!requested) stopSilence(); } catch (RuntimeException error) { requested = false; stopSilence(); Log.e(TAG, "无法请求蓝牙路由", error); } refresh();
    }
    private void clearRoute() {
        if (audio == null) return; handler.removeCallbacks(routeAttempt); attempts = 0;
        try { audio.clearCommunicationDevice(); audio.setMode(AudioManager.MODE_NORMAL); audio.stopBluetoothSco(); } catch (RuntimeException error) { Log.e(TAG, "无法清除路由", error); }
        stopSilence(); requested = false; refresh();
    }
    private void refresh() {
        if (diagnostics == null || audio == null) return;
        AudioDeviceInfo now = audio.getCommunicationDevice(); boolean ready = requested && silenceRunning && now != null && (now.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || now.getType() == TYPE_BLE_HEADSET);
        if (ready) { stateTitle.setText("蓝牙 SCO 已就绪"); stateTitle.setTextColor(c(com.google.android.material.R.attr.colorPrimary)); stateDetail.setText("现在启动 WO Mic，并保持本应用运行。耳机麦克风路由会持续维持。"); }
        else if (attempts > 0) { stateTitle.setText("正在连接耳机通话音频…"); stateTitle.setTextColor(c(com.google.android.material.R.attr.colorOnSurface)); stateDetail.setText("正在等待蓝牙 SCO 端点出现，请稍候。"); }
        else { stateTitle.setText("尚未连接蓝牙麦克风"); stateTitle.setTextColor(c(com.google.android.material.R.attr.colorError)); stateDetail.setText("若已连接但仍未生效，请重新连接蓝牙设备后再次启用。"); }
        StringBuilder s = new StringBuilder("本应用请求：").append(requested ? "已接受" : "未启用").append('\n').append("静音通信播放：").append(silenceRunning ? "运行中" : "未运行").append('\n').append("音频模式：").append(audio.getMode() == AudioManager.MODE_IN_COMMUNICATION ? "通信模式" : "普通模式").append('\n').append("当前通信设备：").append(describe(now)).append("\n\n可用通信设备：\n");
        for (AudioDeviceInfo d : audio.getAvailableCommunicationDevices()) s.append("• ").append(describe(d)).append('\n'); diagnostics.setText(s); Log.i(TAG, s.toString());
    }
    private static String describe(AudioDeviceInfo d) { if (d == null) return "无"; return name(d.getType()) + " · " + d.getProductName(); }
    private static String name(int t) { if (t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) return "蓝牙 SCO"; if (t == TYPE_BLE_HEADSET) return BLE_HEADSET; if (t == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return "手机扬声器"; if (t == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) return "手机听筒"; return "设备类型 " + t; }
    private void startSilence() {
        if (silenceRunning) return; int sr = 16000, min = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), size = Math.max(min * 2, sr / 5 * 2);
        silenceTrack = new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(size).setTransferMode(AudioTrack.MODE_STREAM).build();
        if (silenceTrack.getState() != AudioTrack.STATE_INITIALIZED) { silenceTrack.release(); silenceTrack = null; return; } silenceRunning = true; silenceTrack.play(); byte[] zero = new byte[Math.max(640, size / 2)]; silenceThread = new Thread(() -> { while (silenceRunning) try { AudioTrack t = silenceTrack; if (t == null) break; t.write(zero, 0, zero.length, AudioTrack.WRITE_BLOCKING); } catch (RuntimeException e) { silenceRunning = false; } }, "BtMicRoute-Silence"); silenceThread.start();
    }
    private void stopSilence() { silenceRunning = false; if (silenceThread != null) { silenceThread.interrupt(); silenceThread = null; } if (silenceTrack != null) { try { silenceTrack.pause(); silenceTrack.flush(); silenceTrack.release(); } catch (RuntimeException ignored) {} silenceTrack = null; } }
}
