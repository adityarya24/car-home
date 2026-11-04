package dev.yuvaan.carhome;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.UUID;
import java.util.function.Consumer;

// Talks to a Blaupunkt M18 ambient light kit over Bluetooth LE, the way its
// own app (com.szraise.carled.blaupunkt) does. Frames are
// 2E <cmd> <len> <data...> <checksum>, checksum = ~(cmd + len + data).
// Reading a setting is 90 7C <n>; the kit answers 7C <n> <values>, and
// writing it back is 8D <n> <values>. Setting 0 is brightness (bit 0x40 of
// the first value is the on/off switch), setting 2 is the effect (low four
// bits of the first value are the mode; 4 is "cheerful rhythm", which follows
// music through the kit's own mic).
//
// Each tap connects, reads the setting, writes the change and disconnects,
// so the phone app can still connect when the box is idle.
class AmbientLight {
    static final int TOGGLE = 0, MUSIC = 2;
    private static final String TAG = "CarHomeLight", PREFS = "light", MAC = "mac";
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final String[] SERVICES = {"0000ae30-0000-1000-8000-00805f9b34fb", "0000af30-0000-1000-8000-00805f9b34fb",
            "0000ae00-0000-1000-8000-00805f9b34fb", "00010203-0405-0607-0809-0a0b0c0d1910"};

    private final Context c;
    private final int job;
    private final Consumer<String> done;
    private final Handler main = new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tx;
    private BluetoothLeScanner le;
    private ScanCallback scan;
    private boolean finished;
    private final java.util.Set<String> seen = new java.util.HashSet<>();

    // done gets a short word for the button: "On", "Off", "Music", or why it failed.
    static void run(Context c, int job, Consumer<String> done) {
        new AmbientLight(c, job, done).start();
    }

    private AmbientLight(Context c, int job, Consumer<String> done) {
        this.c = c.getApplicationContext();
        this.job = job;
        this.done = done;
    }

    private void start() {
        BluetoothAdapter bt = ((BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (bt == null || !bt.isEnabled()) {
            finish("Bluetooth off");
            return;
        }
        main.postDelayed(() -> finish("No light"), 15000);
        String mac = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(MAC, null);
        if (mac != null) {
            connect(bt.getRemoteDevice(mac));
            return;
        }
        // First time: find the kit. It advertises as "Pocket Link ..."; its app
        // shows any "Pocket" device as "Blaupunkt M18".
        le = bt.getBluetoothLeScanner();
        scan = new ScanCallback() {
            @Override
            public void onScanResult(int type, ScanResult r) {
                String name = r.getScanRecord() != null ? r.getScanRecord().getDeviceName() : null;
                if (name == null) name = r.getDevice().getName();
                if (seen.add(r.getDevice().getAddress())) Log.i(TAG, "saw " + name + " " + r.getDevice().getAddress()
                        + " rssi " + r.getRssi() + " " + (r.getScanRecord() == null ? "" : hex(r.getScanRecord().getBytes())));
                if (name == null || !(name.toLowerCase().startsWith("pocket") || name.contains("M18")) || scan == null) return;
                le.stopScan(this);
                scan = null;
                c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(MAC, r.getDevice().getAddress()).apply();
                Log.i(TAG, "found " + name + " " + r.getDevice().getAddress());
                connect(r.getDevice());
            }
        };
        le.startScan(scan);
        main.postDelayed(() -> {
            if (scan != null) {
                le.stopScan(scan);
                scan = null;
            }
        }, 10000);
    }

    private void connect(BluetoothDevice d) {
        gatt = d.connectGatt(c, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt g, int status, int state) {
                if (state == BluetoothProfile.STATE_CONNECTED) g.discoverServices();
                else if (state == BluetoothProfile.STATE_DISCONNECTED) finish("No light");
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt g, int status) {
                for (BluetoothGattService svc : g.getServices())
                    for (BluetoothGattCharacteristic ch : svc.getCharacteristics())
                        Log.i(TAG, "has " + svc.getUuid() + " / " + ch.getUuid() + " props " + ch.getProperties());
                BluetoothGattCharacteristic rx = null;
                for (String s : SERVICES) {
                    BluetoothGattService svc = g.getService(UUID.fromString(s));
                    if (svc == null) continue;
                    for (BluetoothGattCharacteristic ch : svc.getCharacteristics()) {
                        int p = ch.getProperties();
                        if ((p & (BluetoothGattCharacteristic.PROPERTY_WRITE
                                | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0 && tx == null) tx = ch;
                        if ((p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 && rx == null) rx = ch;
                    }
                    if (tx != null && rx != null) break;
                    tx = rx = null;
                }
                if (tx == null) {
                    finish("Not M18");
                    return;
                }
                g.setCharacteristicNotification(rx, true);
                BluetoothGattDescriptor cccd = rx.getDescriptor(CCCD);
                if (cccd == null) {
                    finish("Not M18");
                    return;
                }
                cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                g.writeDescriptor(cccd);
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
                send(frame(0x90, 0x7C, job));
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {
                byte[] v = ch.getValue();
                Log.i(TAG, "got " + hex(v));
                // Our answer: 2E 7C <len> <job> <values...> <checksum>
                if (v == null || v.length < 6 || v[0] != 0x2E || (v[1] & 255) != 0x7C || v[3] != job) return;
                int[] vals = new int[(v[2] & 255) - 1];
                for (int i = 0; i < vals.length; i++) vals[i] = v[4 + i] & 255;
                String word;
                if (job == TOGGLE) {
                    vals[0] ^= 0x40;
                    word = (vals[0] & 0x40) != 0 ? "On" : "Off";
                } else {
                    vals[0] = (vals[0] & 0xF0) | 4;
                    word = "Music";
                }
                int[] data = new int[vals.length + 1];
                data[0] = job;
                System.arraycopy(vals, 0, data, 1, vals.length);
                send(frame(0x8D, data));
                main.postDelayed(() -> finish(word), 400);
            }
        }, BluetoothDevice.TRANSPORT_LE);
    }

    private void send(byte[] f) {
        Log.i(TAG, "send " + hex(f));
        tx.setValue(f);
        gatt.writeCharacteristic(tx);
    }

    static byte[] frame(int cmd, int... data) {
        byte[] f = new byte[data.length + 4];
        f[0] = 0x2E;
        f[1] = (byte) cmd;
        f[2] = (byte) data.length;
        int sum = cmd + data.length;
        for (int i = 0; i < data.length; i++) {
            f[3 + i] = (byte) data[i];
            sum += (byte) data[i];
        }
        f[f.length - 1] = (byte) (sum ^ 255);
        return f;
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        if (b != null) for (byte x : b) s.append(String.format("%02X ", x));
        return s.toString().trim();
    }

    private void finish(String word) {
        main.post(() -> {
            if (finished) return;
            finished = true;
            main.removeCallbacksAndMessages(null);
            if (scan != null) le.stopScan(scan);
            if (gatt != null) {
                gatt.disconnect();
                gatt.close();
            }
            done.accept(word);
        });
    }
}
