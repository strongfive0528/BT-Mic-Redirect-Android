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

/** Maintains a communication route only; it never creates AudioRecord or requests RECORD_AUDIO. */
public final class MainActivity extends Activity {
    private static final String TAG = "BTRedirect";
    private static final int TYPE_BLE_HEADSET = 26;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audio;
    private TextView stateTitle, stateDetail, diagnostics;
    private boolean requested, silenceRunning;
    private AudioTrack silenceTrack;
    private Thread silenceThread;
    private int attempts;
    private final Runnable routeAttempt = this::applyRoute;
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 1000); }
    };
    private final AudioDeviceCallback devices = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] ignored) { refresh(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] ignored) { refresh(); }
    };

    @Override public void onCreate(Bundle state) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(state);
        audio = getSystemService(AudioManager.class);
        audio.registerAudioDeviceCallback(devices, handler);
        setContentView(content());
        refresh();
    }

    private View content() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout page = column(20); page.setPadding(dp(20), dp(28), dp(20), dp(28)); scroll.addView(page);
        page.addView(text(R.string.eyebrow, 14, c(com.google.android.material.R.attr.colorPrimary)));
        TextView title = text(R.string.screen_title, 30, c(com.google.android.material.R.attr.colorOnSurface)); title.setPadding(0, dp(4), 0, 0); page.addView(title);
        TextView sub = text(R.string.subtitle, 16, c(com.google.android.material.R.attr.colorOnSurfaceVariant)); sub.setPadding(0, dp(6), 0, dp(20)); page.addView(sub);

        MaterialCardView state = card(c(com.google.android.material.R.attr.colorSurfaceContainerLow)); LinearLayout box = column(20);
        stateTitle = text(R.string.checking_audio, 20, c(com.google.android.material.R.attr.colorOnSurface));
        stateDetail = text(R.string.connect_then_enable, 14, c(com.google.android.material.R.attr.colorOnSurfaceVariant)); stateDetail.setPadding(0, dp(6), 0, 0);
        box.addView(stateTitle); box.addView(stateDetail); state.addView(box); page.addView(state, lp(16));

        MaterialButton enable = new MaterialButton(this); enable.setText(R.string.enable_route); enable.setTextSize(16); enable.setIconResource(android.R.drawable.ic_media_play); enable.setOnClickListener(v -> requestRoute()); page.addView(enable, lp(10));
        MaterialButton disable = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle); disable.setText(R.string.clear_route); disable.setIconResource(android.R.drawable.ic_menu_close_clear_cancel); disable.setOnClickListener(v -> clearRoute()); page.addView(disable, lp(20));

        MaterialCardView reconnect = card(c(com.google.android.material.R.attr.colorErrorContainer)); LinearLayout warn = column(18);
        warn.addView(text(R.string.reconnect_title, 17, c(com.google.android.material.R.attr.colorOnErrorContainer)));
        TextView advice = text(R.string.reconnect_body, 14, c(com.google.android.material.R.attr.colorOnErrorContainer)); advice.setPadding(0, dp(6), 0, 0); warn.addView(advice); reconnect.addView(warn); page.addView(reconnect, lp(20));
        page.addView(text(R.string.details, 16, c(com.google.android.material.R.attr.colorOnSurface)), lp(8));
        MaterialCardView details = card(c(com.google.android.material.R.attr.colorSurfaceContainerLow)); diagnostics = text("", 13, c(com.google.android.material.R.attr.colorOnSurfaceVariant)); diagnostics.setTextIsSelectable(true); diagnostics.setTypeface(android.graphics.Typeface.MONOSPACE); details.addView(diagnostics, new MaterialCardView.LayoutParams(-1, -2) {{ setMargins(dp(16), dp(16), dp(16), dp(16)); }}); page.addView(details);
        return scroll;
    }

    private MaterialCardView card(int color) { MaterialCardView v = new MaterialCardView(this); v.setCardBackgroundColor(color); v.setCardElevation(0); v.setRadius(dp(24)); return v; }
    private LinearLayout column(int padding) { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(padding, padding, padding, padding); return v; }
    private TextView text(int id, float size, int color) { return text(getString(id), size, color); }
    private TextView text(String value, float size, int color) { TextView v = new TextView(this); v.setText(value); v.setTextSize(TypedValue.COMPLEX_UNIT_SP, size); v.setTextColor(color); v.setLineSpacing(0, 1.15f); return v; }
    private LinearLayout.LayoutParams lp(int bottom) { LinearLayout.LayoutParams v = new LinearLayout.LayoutParams(-1, -2); v.bottomMargin = dp(bottom); return v; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int c(int attr) { return MaterialColors.getColor(this, attr, Color.MAGENTA); }

    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(refreshTask); handler.post(refreshTask); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(refreshTask); }
    @Override protected void onDestroy() { handler.removeCallbacks(refreshTask); handler.removeCallbacks(routeAttempt); audio.unregisterAudioDeviceCallback(devices); if (isFinishing()) clearRoute(); super.onDestroy(); }

    private void requestRoute() {
        handler.removeCallbacks(routeAttempt); attempts = 20;
        try { audio.setMode(AudioManager.MODE_IN_COMMUNICATION); startSilence(); audio.startBluetoothSco(); applyRoute(); }
        catch (RuntimeException error) { requested = false; stopSilence(); Log.e(TAG, "Could not start route", error); refresh(); }
    }
    @SuppressWarnings("deprecation") private void applyRoute() {
        AudioDeviceInfo target = null;
        for (AudioDeviceInfo d : audio.getAvailableCommunicationDevices()) if (d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || d.getType() == TYPE_BLE_HEADSET) { target = d; break; }
        if (target == null) { if (--attempts > 0) handler.postDelayed(routeAttempt, 500); else { audio.setMode(AudioManager.MODE_NORMAL); stopSilence(); } refresh(); return; }
        try { requested = audio.setCommunicationDevice(target); if (!requested) stopSilence(); } catch (RuntimeException error) { requested = false; stopSilence(); Log.e(TAG, "Could not request Bluetooth route", error); } refresh();
    }
    private void clearRoute() {
        if (audio == null) return; handler.removeCallbacks(routeAttempt); attempts = 0;
        try { audio.clearCommunicationDevice(); audio.setMode(AudioManager.MODE_NORMAL); audio.stopBluetoothSco(); } catch (RuntimeException error) { Log.e(TAG, "Could not clear route", error); }
        stopSilence(); requested = false; refresh();
    }
    private void refresh() {
        if (diagnostics == null || audio == null) return;
        AudioDeviceInfo now = audio.getCommunicationDevice(); boolean ready = requested && silenceRunning && now != null && (now.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || now.getType() == TYPE_BLE_HEADSET);
        if (ready) { stateTitle.setText(R.string.sco_ready); stateTitle.setTextColor(c(com.google.android.material.R.attr.colorPrimary)); stateDetail.setText(R.string.ready_detail); }
        else if (attempts > 0) { stateTitle.setText(R.string.connecting); stateTitle.setTextColor(c(com.google.android.material.R.attr.colorOnSurface)); stateDetail.setText(R.string.connecting_detail); }
        else { stateTitle.setText(R.string.not_connected); stateTitle.setTextColor(c(com.google.android.material.R.attr.colorError)); stateDetail.setText(R.string.not_connected_detail); }
        StringBuilder text = new StringBuilder();
        text.append(getString(R.string.request_state, getString(requested ? R.string.accepted : R.string.not_enabled))).append('\n');
        text.append(getString(R.string.silent_playback, getString(silenceRunning ? R.string.running : R.string.not_running))).append('\n');
        text.append(getString(R.string.audio_mode, getString(audio.getMode() == AudioManager.MODE_IN_COMMUNICATION ? R.string.communication_mode : R.string.normal_mode))).append('\n');
        text.append(getString(R.string.current_device, describe(now))).append("\n\n").append(getString(R.string.available_devices)).append('\n');
        for (AudioDeviceInfo device : audio.getAvailableCommunicationDevices()) text.append("• ").append(describe(device)).append('\n');
        diagnostics.setText(text); Log.i(TAG, text.toString());
    }
    private String describe(AudioDeviceInfo device) { if (device == null) return getString(R.string.none); return getString(R.string.device_description, name(device.getType()), device.getProductName()); }
    private String name(int type) { if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) return getString(R.string.bluetooth_sco); if (type == TYPE_BLE_HEADSET) return getString(R.string.ble_headset); if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return getString(R.string.speaker); if (type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) return getString(R.string.earpiece); return getString(R.string.device_type, type); }
    private void startSilence() {
        if (silenceRunning) return; int rate = 16000, min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), size = Math.max(min * 2, rate / 5 * 2);
        silenceTrack = new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(size).setTransferMode(AudioTrack.MODE_STREAM).build();
        if (silenceTrack.getState() != AudioTrack.STATE_INITIALIZED) { silenceTrack.release(); silenceTrack = null; return; } silenceRunning = true; silenceTrack.play(); byte[] zero = new byte[Math.max(640, size / 2)]; silenceThread = new Thread(() -> { while (silenceRunning) try { AudioTrack track = silenceTrack; if (track == null) break; track.write(zero, 0, zero.length, AudioTrack.WRITE_BLOCKING); } catch (RuntimeException ignored) { silenceRunning = false; } }, "BTRedirect-Silence"); silenceThread.start();
    }
    private void stopSilence() { silenceRunning = false; if (silenceThread != null) { silenceThread.interrupt(); silenceThread = null; } if (silenceTrack != null) { try { silenceTrack.pause(); silenceTrack.flush(); silenceTrack.release(); } catch (RuntimeException ignored) {} silenceTrack = null; } }
}
