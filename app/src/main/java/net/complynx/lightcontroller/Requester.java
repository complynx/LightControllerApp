package net.complynx.lightcontroller;

import android.Manifest;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

public class Requester extends Service {
    public static final int NOT_DEFINED=0;
    public static final int UPDATE_STATE=1;
    public static final int UPDATE_STATE_TEST=2;
    public static final int TOGGLE_MAIN=0x11;
    public static final int TOGGLE_RGB=0x12;
    public static final int NEED_URL=0xff01;
    public static final int CHECK_CREDENTIALS=0xff02;
    public static final String SESSION_KEY_0 = "00000000000000000000000000000000"; // 32 zeros
    public static final byte[] CA7ADDED_CRC32_SPOOFER={
            (byte)0b11011100, // a byte input for CRC to result in CA7ADDED
            (byte)0b00101011,
            (byte)0b10111100,
            (byte)0b01011111
    };
    public static final int PORT=7867;


    public static final int UDP_PACKET_LENGTH_MAX=256;
    private static final String TAG="CLX.Requester";
    private ServiceHandler mServiceHandler;
    private SharedPreferences settings;
    private SharedPreferences state;

    public static CRC32 crc32_with_cat(){
        CRC32 ret = new CRC32();
        ret.update(CA7ADDED_CRC32_SPOOFER);// since cat can't be added directly to CRC32 in Java, we use madler/spoof to calculate the necessary input to add a cat.
        return ret;
    }
    public static byte[] crc32_bytes(CRC32 crc){
        return ByteBuffer.allocate(4).putInt((int)crc.getValue())
                .order(ByteOrder.nativeOrder()).array();
    }
    public static byte[] getMacAddr() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif: all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                return nif.getHardwareAddress();
            }
        } catch (Exception ex) {}
        return null;
    }
    public static boolean ncmp(byte[] A1,int i1,byte[] A2,int i2, int N){
        boolean ret=true;
        for(int i=0;i<N;++i,++i1,++i2){
            if(A1[i1] != A2[i2]) return false;
        }
        return ret;
    }

    public static InetAddress getLocalIp() {
        Log.i(TAG,"Getting local address!");
        byte[] myself=getMacAddr();
        if(myself == null) { // We assume we never get to this, but if we do, we are saved
            myself = new byte[6];
            myself[0]=2;
            myself[1]=0;// from 2:0:0:0:0:0 of not-found mac of Android
            myself[2]=(byte)0xCA;// CATLISTENull
            myself[3]=0x71;
            myself[4]=0x57;
            myself[5]=(byte)0xE0;
        }
        final byte[] finalMyself = myself;
        final InetAddress[] found_address = {null};
        Thread receiver = new Thread(new Runnable() {
            private static final String TAG="CLX.Requester.UDPReceiver";

            boolean isMyself(byte[] recv) { return isMyself(recv, 0); }
            boolean isMyself(byte[] recv, int offset){
                return ncmp(recv, offset, finalMyself, 0, 6);
            }
            public void run() {
                try {
                    //Keep a socket open to listen to all the UDP trafic that is destined for this port
                    DatagramSocket socket = new DatagramSocket(PORT, InetAddress.getByName("0.0.0.0"));
                    socket.setBroadcast(true);
                    socket.setReuseAddress(true);

                    while(!Thread.currentThread().isInterrupted()) {
                        Log.i(TAG,"Ready to receive broadcast packets!");

                        //Receive a packet
                        byte[] recvBuf = new byte[UDP_PACKET_LENGTH_MAX];
                        DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
                        socket.receive(packet);
                        int length;
                        if((length=packet.getLength()) < 18) {
                            Log.d(TAG, "Packet is too small, skipping");
                            continue; // definitely not our packet, because src[6]+cmd[2]+dest[6]+payload[0+]+crc[4] >= 18
                        }

                        CRC32 crc = crc32_with_cat();
                        byte[] recv = packet.getData();
                        crc.update(recv, 0, length - 4);
                        if(ncmp(recv, length-5, crc32_bytes(crc), 0, 4)) {
                            Log.d(TAG, "Wrong CRC, skipping");
                            continue;
                        }

                        if(isMyself(recv)) {
                            Log.d(TAG, "Packet from myself, skipping");
                            continue;
                        }

                        if(!isMyself(recv, 8)){
                            Log.d(TAG, "Packet not for me, skipping");
                            continue;
                        }

                        if(recv[6] != 0x03 || recv[7] != 0x02){
                            Log.d(TAG, "Packet has wrong answer");

                            StringBuilder recv_readable = new StringBuilder();
                            for (int i=0; i< packet.getLength(); ++i) {
                                recv_readable.append(String.format("%02X ", recv[i]));
                            }
                            Log.i(TAG, "Packet received; data: " + recv_readable);
                            continue;
                        }

                        Log.i(TAG, "Packet received from: " + packet.getAddress().getHostAddress());
                        found_address[0] = packet.getAddress();
                        return;
                    }
                } catch (Exception ex) {
                    Log.i(TAG, "Oops" + ex.getMessage());
                }
            }
        });
        receiver.start();

        Thread sender = new Thread(new Runnable() {
            private static final String TAG="CLX.Requester.UDPSender";
            public void run() {
                try {
                    DatagramSocket socket = new DatagramSocket();
                    socket.setBroadcast(true);
                    socket.setReuseAddress(true);

                    byte[] sendData = new byte[18]; // myself[6] cmd[2] null_address[6] crc[4]
                    System.arraycopy(finalMyself, 0, sendData, 0, 6);
                    sendData[6] = 1; // QUERY_TYPE_DISCOVERY
                    sendData[7] = 2; // DISCOVERY_STATUS
                    CRC32 crc = crc32_with_cat();
                    crc.update(sendData, 0, 14); // last 4 are src
                    System.arraycopy(crc32_bytes(crc),0, sendData, 14, 4);
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, getBroadcastAddress(), PORT);
                    socket.send(sendPacket);
                    Log.i(TAG, "Broadcast packet sent to: " + getBroadcastAddress().getHostAddress());
                } catch (Exception ex) {
                    Log.i(TAG, "Oops " + ex.getMessage());
                }
            }
            InetAddress getBroadcastAddress() throws IOException {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

                while (interfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaces.nextElement();
                    if (networkInterface.isLoopback()) continue; // Don't want to broadcast to the loopback interface

                    for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                        InetAddress broadcast = interfaceAddress.getBroadcast();

                        if (broadcast == null) continue;

                        return broadcast;
                    }
                }
                return null;
            }
        });
        sender.start();
        try {
            int iter=100; // 3 sec = 100 * 30 milliseconds
            while(found_address[0] == null && --iter > 0){
                TimeUnit.MILLISECONDS.sleep(30);
            }
        } catch (InterruptedException consumed) {}
        Log.i(TAG, "Terminating receiver");
        receiver.interrupt();

        return found_address[0];
    }

    public static JSONObject getJson(String url, JSONObject request){
        InputStream is;

        try {
            URL _url = new URL(url);
            HttpURLConnection urlConn =(HttpURLConnection)_url.openConnection();
            urlConn.setRequestMethod("POST");
            urlConn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            urlConn.setRequestProperty("Accept", "application/json");
            urlConn.setDoOutput(true);
            urlConn.connect();

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(urlConn.getOutputStream()));
            Log.d(TAG, "Sending request to " + url + ":\n" + request.toString(4));
            writer.write(request.toString());
            writer.flush();
            writer.close();

            if(urlConn.getResponseCode() == HttpURLConnection.HTTP_OK){
                is = urlConn.getInputStream();// is is inputstream
            } else {
                is = urlConn.getErrorStream();
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    is, StandardCharsets.UTF_8), 8);
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            is.close();
            JSONObject response = new JSONObject(sb.toString());
            Log.d(TAG,"Got response: "+ response.toString(4));

            return response;
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return null;
    }

    public static JSONObject buildUbusRequest_a(String session_key, String ubus_obj, String method,
                                              Object[] params){
        JSONObject req = new JSONObject();
        try {
            req.put("jsonrpc", "2.0");
            req.put("id", 1);
            req.put("method", "call");
            JSONArray req_params = new JSONArray();
            req_params.put(session_key);
            req_params.put(ubus_obj);
            req_params.put(method);
            for (Object param : params) {
                req_params.put(param);
            }
            req.put("params", req_params);
        } catch (JSONException e) {
            Log.e(TAG, "JSON Exception " + e.getMessage());
        }
        return req;
    }
    public static JSONObject buildUbusRequest(String session_key, String ubus_obj, String method,
                                              Object... params){
        return buildUbusRequest_a(session_key, ubus_obj, method, params);
    }

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        Requester requester;
        String url;

        ServiceHandler(Looper looper, Requester _requester) {
            super(looper);
            requester = _requester;
        }

        JSONObject sendUbusRequestNSK_a(String session_key, String ubus_obj, String method,
                                   Object[] params){
            Log.d(TAG, "Sending request " + ubus_obj + ":" + method + " S:"+ session_key);
            JSONObject req = buildUbusRequest_a(session_key, ubus_obj, method, params);

            JSONObject response = getJson(url, req);

            if(response == null){
                Log.d(TAG, "Request error: null response");
                return null;
            }
            try{
                if(response.has("error")){
                    Log.d(TAG, "Request error: " + response.getJSONObject("error").toString(4));
                    return null;
                }
                if(response.has("result")) return response.getJSONArray("result").getJSONObject(1);
            } catch (JSONException e){
                Log.e(TAG, "JSON error: " + e.getMessage());
            }
            return null;
        }
        JSONObject sendUbusRequestNSK(String session_key, String ubus_obj, String method,
                                      Object... params){
            return sendUbusRequestNSK_a(session_key,ubus_obj,method,params);
        }

        String login(){
            String session_key = SESSION_KEY_0;
            Log.d(TAG, "Loggin in...");

            SharedPreferences.Editor editor = settings.edit();
            editor.putString("session-key", SESSION_KEY_0);
            editor.putLong("session-key expires", 0);
            editor.apply();

            String user = settings.getString("User", "");
            String pass = settings.getString("Password", "");

            if(user.equals("") || pass.equals("")){
                Log.d(TAG, "NEED SETTING");
                Intent intent = new Intent(requester, Settings.class);
                intent.putExtra("T", NEED_URL);
                requester.startActivity(intent);
                return SESSION_KEY_0;
            }

            try {
                JSONObject req_param_login = new JSONObject();
                req_param_login.put("username", user);
                req_param_login.put("password", pass);

                Log.d(TAG, "Sending login request");
                JSONObject response = sendUbusRequestNSK(session_key, "session",
                        "login", req_param_login);

                if(response == null){
                    Log.d(TAG, "WRONG USER/PASSWORD");
                    Intent intent = new Intent(requester, Settings.class);
                    intent.putExtra("T", CHECK_CREDENTIALS);
                    requester.startActivity(intent);
                    return SESSION_KEY_0;
                }

                session_key = response.getString("ubus_rpc_session");
                long expires = response.getInt("expires") - 1;
                Log.d(TAG, "Got session key " + session_key + " for " + expires + " seconds");
                expires *= 1000;
                expires += System.currentTimeMillis();

                editor.putString("session-key", session_key);
                editor.putLong("session-key expires", expires);
                editor.apply();

            } catch (JSONException e) {
                Log.e(TAG, "JSON Exception " + e.getMessage());
                session_key = SESSION_KEY_0;
            }

            return session_key;
        }

        JSONObject sendUbusRequest_a(String ubus_obj, String method, Object[] params){
            String session_key = settings.getString("session-key", SESSION_KEY_0);
            long expires = settings.getLong("session-key expires", 0);
            JSONObject ret;

            if(session_key.equals(SESSION_KEY_0) || expires < System.currentTimeMillis()){
                session_key = login();
                if(session_key.equals(SESSION_KEY_0)){
                    return null;
                }
            }

            Log.d(TAG, "Sending request " + ubus_obj + ":" + method);
            ret = sendUbusRequestNSK_a(session_key, ubus_obj, method, params);
            if(ret == null){
                session_key = login();
                if(session_key.equals(SESSION_KEY_0)){
                    return null;
                }
                Log.d(TAG, "Sending 2nd request " + ubus_obj + ":" + method);
                ret = sendUbusRequestNSK_a(session_key, ubus_obj, method, params);
            }

            return ret;
        }
        JSONObject sendUbusRequest(String ubus_obj, String method, Object... params){
            return sendUbusRequest_a(ubus_obj, method, params);
        }

        void sendRGBCommand(String method, JSONObject params){
            JSONObject response = sendUbusRequest("rgbdriver", method, params);

            if(response == null){
                Log.d(TAG, "...Not today...");
                return;
            }
            try {
                Log.d(TAG, "Got status: " + response.toString(4));
                updateState(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        void getState(){ sendRGBCommand("status", new JSONObject()); }
        void toggleRGB(){ sendRGBCommand("togglergb", new JSONObject()); }
        void toggleMain(){ sendRGBCommand("togglemain", new JSONObject()); }

        void setRGB(){
            sendRGBCommand("togglemain", new JSONObject());
        }

        void updateState(JSONObject j_state){
            if(j_state == null) return;
            try{
                j_state = j_state.getJSONObject("status");
                SharedPreferences.Editor editor = state.edit();

                editor.putLong("last update", System.currentTimeMillis());
                editor.putInt("color", j_state.getInt("color"));
                editor.putInt("main", j_state.getBoolean("main")? 1:0);
                editor.putInt("transition", j_state.getInt("transition"));
                editor.putInt("seconds", j_state.getInt("seconds"));
                editor.putInt("useconds", j_state.getInt("useconds"));
                editor.putInt("position", j_state.getInt("position"));
                editor.putInt("target", j_state.getInt("target"));

                editor.apply();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            Intent intent = new Intent(requester, RGBLightController.class);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getApplication());
            int[] ids = appWidgetManager.getAppWidgetIds(new ComponentName(getApplication(), RGBLightController.class));
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            sendBroadcast(intent);
        }

        boolean validateLocal_(){
            String addr = state.getString("local address", "");
            if(addr.equals("")) return false;
            WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if(wm == null) return false;
            WifiInfo wi = wm.getConnectionInfo();
            if(wi == null) return false;
            if(wi.getSupplicantState() != SupplicantState.COMPLETED) return false;

            String wbssid = wi.getBSSID();
            String wssid = wi.getSSID();
            String sbssid = state.getString("wi bssid", "");
            String s_ssid = state.getString("wi ssid", "");

            return !wbssid.equals("") && !wbssid.equals("02:00:00:00:00:00")
                    && wbssid.equals(sbssid) && s_ssid.equals(wssid);
        }
        boolean validateLocal(){
            if(!validateLocal_()){
                Log.d(TAG, "Local IP is invalid...");
                SharedPreferences.Editor editor = state.edit();
                editor.putString("wi bssid", "");
                editor.putString("wi ssid", "");
                editor.putString("local address", "");
                editor.apply();
                return false;
            }
            Log.d(TAG, "Local IP seem valid...");
            return true;
        }

        void saveLocalIp(InetAddress addr){
            WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if(wm == null) return;
            WifiInfo wi = wm.getConnectionInfo();
            if(wi == null) return;
            String wbssid = wi.getBSSID();
            String wssid = wi.getSSID();

            SharedPreferences.Editor editor = state.edit();
            editor.putString("wi bssid", wbssid);
            editor.putString("wi ssid", wssid);
            editor.putString("local address", addr.getHostAddress());
            editor.apply();
        }

        void updateLocalIp(){
            Thread updater = new Thread(new Runnable() {
                private static final String TAG="CLX.Requester.IPUpdater";
                public void run() {
                    Log.d(TAG, "WiFi testing...");
                    if (ContextCompat.checkSelfPermission(requester, Manifest.permission.ACCESS_COARSE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED){

                        Log.d(TAG, "No permission");
                        Intent intent = new Intent(requester, Settings.class);
                        intent.putExtra("T", NEED_URL);
                        requester.startActivity(intent);
                        return;
                    }

                    WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                    if(wm == null){
                        Log.d(TAG, "wm == null");
                        return;
                    }
                    WifiInfo wi = wm.getConnectionInfo();
                    if(wi == null){
                        Log.d(TAG, "wi == null");
                        return;
                    }
                    String wbssid = wi.getBSSID();
                    if(wbssid.equals("") || wbssid.equals("02:00:00:00:00:00")){
                        Log.d(TAG, "wbssid = " + wbssid);
                        return;
                    }
                    if(wi.getSupplicantState() != SupplicantState.COMPLETED){
                        Log.d(TAG, "SupplicantState = " + wi.getSupplicantState().toString());
                        return;
                    }
                    Log.d(TAG, "WiFi ok, updating IP...");

                    InetAddress addr = getLocalIp();
                    if(addr != null) {
                        String local_url_postfix = settings.getString("Local link", "");
                        Log.d(TAG, "Got local address " + addr.getHostAddress());

                        url = "http://" + addr.getHostAddress() + local_url_postfix;

                        Log.d(TAG, "Url: " + url);

                        saveLocalIp(addr);
                    }else{
                        Log.d(TAG, "Local IP not found...");
                    }
                }
            });
            updater.start();
        }

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "Message handler...");
            url = settings.getString("Remote link", "");
            String local_url_postfix = settings.getString("Local link", "");

            if(url.equals("") || local_url_postfix.equals("")){
                Intent intent = new Intent(requester, Settings.class);
                intent.putExtra("T", NEED_URL);
                requester.startActivity(intent);
                stopSelf(msg.arg1);
                return;
            }

            if(validateLocal()) {
                url = "http://" + state.getString("local address", "") + local_url_postfix;
            } else {
                updateLocalIp();
            }
            Log.d(TAG, "Using URL: " + url);

            switch (msg.arg2){
                case UPDATE_STATE:
                case UPDATE_STATE_TEST:
                    getState();
                    break;
                case TOGGLE_MAIN:
                    toggleMain();
                    break;
                case TOGGLE_RGB:
                    toggleRGB();
                    break;
                default:
                    Log.d(TAG, "Message Type is not supported");
            }

            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf(msg.arg1);
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Created");
        settings = getSharedPreferences("settings", MODE_PRIVATE);
        state = getSharedPreferences("state", MODE_PRIVATE);
        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceHandler = new ServiceHandler(thread.getLooper(), this);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int type = intent.getIntExtra("T", NOT_DEFINED);
        Log.d(TAG, "service starting " + type);

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.arg2 = type;
        msg.setData(intent.getBundleExtra("data"));
        mServiceHandler.sendMessage(msg);

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }



    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroyed");
        // Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
    }

}
