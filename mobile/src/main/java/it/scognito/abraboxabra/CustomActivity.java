package it.scognito.abraboxabra;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.OpacityBar;
import com.larswerkman.holocolorpicker.SVBar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import static it.scognito.abraboxabra.R.id.opacitybar;

public class CustomActivity extends Activity{

    final int HANDLE_STATUS = 0;
    final int HANDLE_RET_MSG = 1;
    final int REQUEST_ENABLE_BT = 0;
    final int STATUS_DISCONNECTED = 0;
    final int STATUS_CONNECTED = 1;
    final int STATUS_BUSY = 2;
    /*
        Switch autoPushOnConnect;
    */
    final String myUuid = "94f39d29-7d6d-437d-973b-fba39e49d4ee";
    final int DEBUG_MSG_ERROR = 0, DEBUG_MSG_INFO = 1;
    private final String TAG = "ABRABOXABRA";
    ImageView ivStatus, ivInfo;
    TextView tvLog, last_change;
    SeekBar seekSize, seekDuration, seekAngle, seekDirection;
    Spinner spinner;
    int steeringAngle = 180, speed = 20, duration = 2, size = 3 , direction = 0, lastColor =0;
    long lastChange;

    ColorPicker picker;
    SVBar svBar;
    OpacityBar opacityBar;

    String btServerAddr = null;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice btDevice = null;
    boolean autoPush = false;
    private int status = STATUS_DISCONNECTED;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private Handler messageHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HANDLE_STATUS:
                    //super.handleMessage(msg);
                    //Log.d(TAG, "******* arg1: " + msg.arg1 + " arg2: " + msg.arg2  + " object: " + msg.obj.toString());
                    setStatus(Integer.parseInt(msg.obj.toString()));
                    break;
                case HANDLE_RET_MSG:
                    showServerDevices(msg.obj.toString());
                    puppaLog(DEBUG_MSG_INFO, "HANDLE_RET_MSG: " + msg.obj.toString());
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("Start","Start custom activity");
        setContentView(R.layout.activity_customize);

        getAbraboxabraAddress();
        setupViews();


/*
        getAutoPushOnConnect();
*/
        puppaLog(DEBUG_MSG_INFO, "App start!");

        /*
        if (!btInit())
            return;

        startBT();
        */

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

    }

    @Override
    protected void onStop() {
        super.onStop();
        btStopService();
        Log.d(TAG, "Stopped");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!btInit())
            return;

        if (status != STATUS_CONNECTED) {
            startBT();
        }
        Log.d(TAG, "Resumed");
    }

    private void startBT() {
        if (btServerAddr == null)
            selectBtDevice();
        else {
            if (btQueryPairedDevices())
                btStartService();
        }
    }

    private boolean btInit() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            puppaLog(DEBUG_MSG_INFO, "Device does not support Bluetooth!");
            return false;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        return true;
    }

    private boolean btQueryPairedDevices() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            puppaLog(DEBUG_MSG_INFO, "Found " + pairedDevices.size() + " previously paired devices");
            for (BluetoothDevice device : pairedDevices) {
                // if (device.getName().equalsIgnoreCase(btServerName)) {
                if (device.getAddress().equalsIgnoreCase(btServerAddr)) {
                    puppaLog(DEBUG_MSG_INFO, "Found device: " + device.getName() + " -- " + device.getAddress());
                    btDevice = device;
                    return true;
                } else
                    puppaLog(DEBUG_MSG_INFO, "Device " + device.getName() + " is not an abraboxabra server");
            }
        } else
            puppaLog(DEBUG_MSG_INFO, "No previously paired device found");

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                puppaLog(DEBUG_MSG_INFO, "Enabled OK!");
                startBT();
            } else {
                puppaLog(DEBUG_MSG_INFO, "ERROR: " + resultCode);
            }
        }
    }

    public void setupViews() {
        ivStatus = (ImageView) findViewById(R.id.ivStatus);
        ivInfo = (ImageView) findViewById(R.id.ivInfo);
        tvLog = (TextView) findViewById(R.id.tvLog);
        tvLog.setMovementMethod(new ScrollingMovementMethod());

        seekAngle = (SeekBar) findViewById(R.id.seekAngle);
        seekDirection = (SeekBar) findViewById(R.id.seekDirection);
        seekDuration = (SeekBar) findViewById(R.id.seekDuration);
        seekSize = (SeekBar) findViewById(R.id.seekSize);
        last_change = (TextView) findViewById(R.id.last_change);

        spinner = (Spinner) findViewById(R.id.spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.animations_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean isLoaded = false;
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (isLoaded) {
                    String type = parentView.getItemAtPosition(position).toString();
                    sendMessage("<Custom,type=" + type + ">");
                    last_change.setText("type: " + type);
                }
                isLoaded = true;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // your code here
            }

        });

        // Setup colorPicker
        picker = (ColorPicker) findViewById(R.id.picker);
        lastChange = System.currentTimeMillis();
        svBar = (SVBar) findViewById(R.id.svbar);
        opacityBar = (OpacityBar) findViewById(opacitybar);
        picker.addSVBar(svBar);
        picker.addOpacityBar(opacityBar);
        picker.setShowOldCenterColor(false);
        picker.setOnColorChangedListener(new ColorPicker.OnColorChangedListener()
        {
            @Override
            public void onColorChanged(int color) {
                last_change.setText("color: "+ color);
                lastColor = color;


                long difference = System.currentTimeMillis() - lastChange;

                if (difference > 500)
                {  // do not send on every color change
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            sendMessage("<Custom,color=" +lastColor+">");
                        }
                    }, 500);}
            }
        });
        
        seekAngle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sendMessage("<Custom,angle=" +steeringAngle+">");

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                steeringAngle = progress;
                last_change.setText("steeringAngle: "+ steeringAngle);

            }
        });
        seekDirection.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sendMessage("<Custom,direction=" +direction+">");

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                direction = progress;
                last_change.setText("direction: "+ direction);

            }
        });
        seekSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sendMessage("<Custom,size=" +size+">");

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                size = progress;
                last_change.setText("size: "+ size);

                //last_change.setText(progress+" mph");
            }
        });

        seekDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sendMessage("<Custom,duration=" +duration+">");

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                duration = progress;
                last_change.setText("duration: "+ duration);

            }
        });


        ivInfo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (tvLog.getVisibility() == View.VISIBLE)
                    tvLog.setVisibility(View.INVISIBLE);
                else
                    tvLog.setVisibility(View.VISIBLE);
            }
        });

        ivStatus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // (new Thread(new workerThread("lightOff"))).start();
                puppaLog(DEBUG_MSG_INFO, "RESTARTING server");
                if (btQueryPairedDevices()) {
                    btStartService();
                }
            }
        });
    }

    private void sendMessage(String message) {
        if (mConnectedThread != null) {
            mConnectedThread.write(message.getBytes());
            puppaLog(DEBUG_MSG_INFO, "send: " + message);
        } else {
            puppaLog(DEBUG_MSG_INFO, message);
        }

    }
    private void sendDelayedMessage(String message) {
        if (mConnectedThread != null) {
            mConnectedThread.write(message.getBytes());
            puppaLog(DEBUG_MSG_INFO, "send: " + message);
        } else {
            puppaLog(DEBUG_MSG_INFO, message);
        }

    }
    private void setBtServer(String addr) {
        btServerAddr = addr;
        if (btQueryPairedDevices()) {
            btStartService();
        }
    }

    public void getAbraboxabraAddress() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        btServerAddr = prefs.getString("btaddress", null);
    }

    public void setAbraboxabraAddress(String addr) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        Editor edit = prefs.edit();
        edit.putString("btaddress", addr);
        edit.apply();
    }

    private void setStatus(int pstatus) {

        switch (pstatus) {
            case STATUS_CONNECTED:
                ivStatus.setImageResource(android.R.drawable.presence_online);
                break;
            case STATUS_DISCONNECTED:
                ivStatus.setImageResource(android.R.drawable.presence_busy);
                break;
            case STATUS_BUSY:
                ivStatus.setImageResource(android.R.drawable.presence_away);
                break;
            default:
                break;
        }
        status = pstatus;
    }

/*    public void getAutoPushOnConnect() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        autoPush = prefs.getBoolean("autopush", false);
        autoPushOnConnect.setChecked(autoPush);
    }
       public void setAutoPushOnConnect(boolean autostart) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        Editor edit = prefs.edit();
        edit.putBoolean("autopush", autostart);
        edit.apply();

        autoPush = autostart;
    }*/

    private void askShutdown() {
        AlertDialog dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.device_no_bt, Toast.LENGTH_SHORT).show();
            return;
        }

        builder.setTitle(getResources().getString(R.string.ask_shutdown));

        builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });

        builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                if (mConnectedThread != null) {
                    mConnectedThread.write("shutdown".getBytes());
                    puppaLog(DEBUG_MSG_INFO, "Sending command");
                }
            }
        });


        dialog = builder.create();
        dialog.show();
    }

    private void enablePairing() {
        if (mConnectedThread != null) {
            mConnectedThread.write("enable_pairing".getBytes());
            puppaLog(DEBUG_MSG_INFO, "Sending command enable_pairing");
            Toast.makeText(this, R.string.send_pairing, Toast.LENGTH_SHORT).show();
        }
    }

    private void showServerDevices(String allDevices) {
        if (allDevices.equalsIgnoreCase("none")) {
            Toast.makeText(this, R.string.no_devices_on_server, Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        String[] devices = allDevices.split("<-->");
        final String[] btDevicesNameArray = new String[devices.length];
        final String[] btDevicesAddrArray = new String[devices.length];

        int i = 0;
        for (String dev : devices) {
            btDevicesAddrArray[i] = (dev.substring(0, 17));
            btDevicesNameArray[i] = (dev.substring(18, dev.length()));
            Log.d(TAG, "->" + btDevicesNameArray[i] + "<-->" + btDevicesAddrArray[i] + "<-");
            i++;
        }

        builder.setTitle(getResources().getString(R.string.select_device_remove));
        builder.setSingleChoiceItems(btDevicesNameArray, 0, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                puppaLog(DEBUG_MSG_INFO, "Selected: " + btDevicesNameArray[item] + "(" + btDevicesAddrArray[item] + ")");
                if (mConnectedThread != null) {
                    mConnectedThread.write(("remove_device " + btDevicesAddrArray[item]).getBytes());
                    puppaLog(DEBUG_MSG_INFO, "Sending command: " + "remove_device " + btDevicesAddrArray[item]);
                }
                // setBtServer(btDevicesAddrArray[item]);
                // setAbraboxabraAddress(btDevicesAddrArray[item]);
                dialog.dismiss();
            }
        });

        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });

        dialog = builder.create();
        dialog.show();

    }

    private void selectBtDevice() {

        AlertDialog dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.device_no_bt, Toast.LENGTH_SHORT).show();
            return;
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() == 0) {
            builder.setTitle(getResources().getString(R.string.warning));
            builder.setMessage(getResources().getString(R.string.no_paired_devices_found));
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                }
            });
            dialog = builder.create();
            dialog.show();
            return;
        } else
            puppaLog(DEBUG_MSG_INFO, "(SB)Found " + pairedDevices.size() + " previously paired devices");

        final String[] btDevicesNameArray = new String[pairedDevices.size()];
        final String[] btDevicesAddrArray = new String[pairedDevices.size()];

        int i = 0;
        for (BluetoothDevice device : pairedDevices) {
            btDevicesNameArray[i] = device.getName() == null ? " " : device.getName();
            btDevicesAddrArray[i] = device.getAddress();
            Log.d(TAG, "name: " + device.getName() + " address: " + device.getAddress());
            i++;
        }

        builder.setTitle(getResources().getString(R.string.select_server));

        builder.setSingleChoiceItems(btDevicesNameArray, 0, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                puppaLog(DEBUG_MSG_INFO, "Selected:" + btDevicesNameArray[item] + "(" + btDevicesAddrArray[item] + ")");
                setBtServer(btDevicesAddrArray[item]);
                setAbraboxabraAddress(btDevicesAddrArray[item]);
                dialog.dismiss();
            }
        });

        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });

        dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        puppaLog(DEBUG_MSG_INFO, "Stop bt service!");
        btStopService();
    }

    public synchronized void connected(BluetoothSocket socket) {

        Log.d(TAG, "Starting mConnectedThread");
        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, messageHandler);
        mConnectedThread.start();
    }

    public synchronized void btStartService() {
        Log.d(TAG, "Start bluetooth service");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {

            Log.d(TAG, "Thread mConnectThread exists, canceling it");
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            Log.d(TAG, "Thread mConnectEDThread exists, canceling it");
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        Log.d(TAG, "Creating ConnectThread...");
        setStatus(STATUS_BUSY);
        mConnectThread = new ConnectThread(btDevice, messageHandler);
        mConnectThread.start();
    }

    public synchronized void btStopService() {
        puppaLog(DEBUG_MSG_INFO, "Stop bluetooth service");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setStatus(STATUS_DISCONNECTED);
    }

    public void puppaLog(int type, String puppa) {

        if (type == DEBUG_MSG_ERROR)
            Log.e(TAG, puppa);
        else
            Log.i(TAG, puppa);

        if (tvLog != null)
            tvLog.append("\n" + puppa);
    }

    /* MENU STUFF */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_select_server:
                selectBtDevice();
                return true;
            case R.id.action_enable_pairing:
                enablePairing();
                return true;
            case R.id.action_remove_device:
                if (mConnectedThread != null) {
                    mConnectedThread.write("device_list".getBytes());
                    puppaLog(DEBUG_MSG_INFO, "Sending command");
                }
                return true;
            case R.id.action_shutdown:
                askShutdown();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public enum loop {
        INF("INF"), OFF("OFF"), ONE("ONE");

        private String value;

        loop(String v) {
            value = v;
        }

        public String getValue() {
            return value;
        }
    }

    /* OTHER CLASSES */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        //private final BluetoothDevice mmDevice;
        private Handler parentHandler;

        public ConnectThread(BluetoothDevice device, Handler parentHandler) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            //   mmDevice = device;
            this.parentHandler = parentHandler;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString(myUuid));
                Log.d(TAG, "ConnectThread Constructor created :)");
            } catch (IOException e) {
                Log.e(TAG, "Error creating socket! " + e.toString());
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();

            try {
                parentHandler.sendMessage(parentHandler.obtainMessage(HANDLE_STATUS, STATUS_BUSY));
                //parentHandler.sendEmptyMessage(STATUS_BUSY);

                /*
                msgObj = parentHandler.obtainMessage();
                Bundle b = new Bundle();
                b.putString("message", msg);
                msgObj.setData(b);
                handler.sendMessage(msgObj);
                */


                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
                Log.d(TAG, "RFCOMM channel created :)");
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                // setStatus(STATUS_DISCONNECTED);
                parentHandler.sendMessage(parentHandler.obtainMessage(HANDLE_STATUS, STATUS_DISCONNECTED));
                //parentHandler.sendEmptyMessage(STATUS_DISCONNECTED);
                try {
                    mmSocket.close();
                    Log.e(TAG, "IOError opening socket!");
                } catch (IOException closeException) {
                    Log.e(TAG, "IOError closing socket!");
                }

                Log.e(TAG, "IOError connecting to socket!");
                return;
            }

            // Do work to manage the connection (in a separate thread)
            connected(mmSocket); //, mmDevice);
        }

        public void cancel() {
            //parentHandler.sendEmptyMessage(STATUS_DISCONNECTED);
            parentHandler.sendMessage(parentHandler.obtainMessage(HANDLE_STATUS, STATUS_DISCONNECTED));
            try {
                Log.d(TAG, "ConnectThread.cancel...");
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "IOError on ConnectThread.cancel! " + e.toString());
            }
        }
    }

    /*
     *
     * THREAD CONNESSIONE
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private Handler parentHandler;

        public ConnectedThread(BluetoothSocket socket, Handler parentHandler) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            this.parentHandler = parentHandler;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
                parentHandler.sendMessage(parentHandler.obtainMessage(HANDLE_STATUS, STATUS_CONNECTED));
                //parentHandler.sendEmptyMessage(STATUS_CONNECTED);

            } catch (IOException e) {
                parentHandler.sendMessage(parentHandler.obtainMessage(HANDLE_STATUS, STATUS_DISCONNECTED));
                //parentHandler.sendEmptyMessage(STATUS_DISCONNECTED);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024]; // buffer store for the stream
            int bytes; // bytes returned from read()

            if (autoPush)
                write("openclose".getBytes());

            // Keep listening to the InputStream until an exception occurs
            Log.d(TAG, "THE FUN BEGINS!");
            while (true) {

                try { // Read from the InputStream bytes =
                    bytes = mmInStream.read(buffer);

                    // Send the obtained bytes to the UI activity //
                    String readMessage = new String(buffer, 0, bytes);
                    parentHandler.sendMessage(parentHandler.obtainMessage(HANDLE_RET_MSG, 0, 0, readMessage));
                    //Log.d(TAG, "Received message: " + readMessage);

                } catch (IOException e) {
                    parentHandler.sendMessage(parentHandler.obtainMessage(HANDLE_STATUS, STATUS_DISCONNECTED));
                    //parentHandler.sendEmptyMessage(STATUS_DISCONNECTED);
                    Log.e(TAG, "IOException: " + e.toString());
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                parentHandler.sendMessage(parentHandler.obtainMessage(HANDLE_STATUS, STATUS_DISCONNECTED));
                //parentHandler.sendEmptyMessage(STATUS_DISCONNECTED);
                Log.e(TAG, "Write error: " + e.toString());
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            parentHandler.sendMessage(parentHandler.obtainMessage(HANDLE_STATUS, STATUS_DISCONNECTED));
            //parentHandler.sendEmptyMessage(STATUS_DISCONNECTED);
            try {
                Log.d(TAG, "ConnectedThread.cancel...");
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread.cancel exception " + e.toString());
            }
        }
    }
}