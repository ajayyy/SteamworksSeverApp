package lakeeffect.ca.scoutingserverapp;

import android.bluetooth.BluetoothSocket;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Created by Ajay on 10/15/2017.
 *
 * Class that deals with pulling the data, this class should not be able to be called multiple times
 */
public class PullDataThread extends Thread{

    boolean running = false;

    BluetoothSocket bluetoothSocket;
    OutputStream out;
    InputStream in;

    MainActivity mainActivity;

    public PullDataThread(BluetoothSocket bluetoothSocket, MainActivity mainActivity){
        this.bluetoothSocket = bluetoothSocket;
        this.mainActivity = mainActivity;
    }

    @Override
    public void run() {
        running = true;

        //set status
        mainActivity.runOnUiThread(new Thread() {
            public void run() {
                mainActivity.status.setText("Connecting to device...");
            }
        });
        //send pull request and wait for a response
        try {
            bluetoothSocket.connect();
            in = bluetoothSocket.getInputStream();
            out = bluetoothSocket.getOutputStream();

            if(mainActivity.labels == null){
                mainActivity.runOnUiThread(new Thread() {
                   public void run() {
                       mainActivity.status.setText("Connected! Requesting Labels...");
                   }
               });

                out.write("REQUEST LABELS".getBytes(Charset.forName("UTF-8")));
                String labels = waitForMessage();

                int version = Integer.parseInt(labels.split(":::")[0]);
                if(version >= mainActivity.minVersionNum){
                    mainActivity.labels = labels.split(":::")[1];
                }else{
                    //send toast saying that the client has a version too old
                    mainActivity.runOnUiThread(new Thread(){
                        public void run(){
                            Toast.makeText(mainActivity, "The Scouting App on the device you connected too is too old, either tell them to update or change the minimum version number", Toast.LENGTH_LONG).show();
                        }
                    });
                    running = false;
                    return;
                }
            }

            mainActivity.runOnUiThread(new Thread() {
                public void run() {
                    mainActivity.status.setText("Connected! Requesting Data...");
                }
            });

            out.write("REQUEST DATA".getBytes(Charset.forName("UTF-8")));
            String message = waitForMessage();

            int version = Integer.parseInt(message.split(":::")[0]);
            if(version < mainActivity.minVersionNum){
                //send toast saying that the client has a version too old
                mainActivity.runOnUiThread(new Thread(){
                    public void run(){
                        Toast.makeText(mainActivity, "The Scouting App on the device you connected too is too old, either tell them to update or change the minimum version number", Toast.LENGTH_LONG).show();
                    }
                });
                running = false;
                return;
            }else{
                String[] data = message.split(":::")[1].split("::");

                for(int i=0;i<data.length;i++){
                    mainActivity.save(data[i], mainActivity.labels);
                }
            }

            out.write("RECEIVED".getBytes(Charset.forName("UTF-8")));

        } catch (IOException e) {
            e.printStackTrace();
        }

        //send toast of completion
        mainActivity.runOnUiThread(new Thread(){
            public void run(){
                mainActivity.status.setText("All ready!");
                Toast.makeText(mainActivity, "Finished getting data and received X amount of data", Toast.LENGTH_LONG).show();
            }
        });


        try {
            in.close();
            out.close();
            bluetoothSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        running = false;
    }

    public String waitForMessage(){
        String finalMessage = "";
        while(out != null && in != null && bluetoothSocket.isConnected()){
            byte[] bytes = new byte[100000];
            int amount = 0;
            try {
                amount = in.read(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(amount>0)  bytes = Arrays.copyOfRange(bytes, 0, amount);//puts data into bytes and cuts bytes
            else continue;

            String message = finalMessage + new String(bytes, Charset.forName("UTF-8"));
            if(!message.endsWith("end")){
                finalMessage = message;
                System.out.println(finalMessage + " message");
                continue;
            }

            return message;
        }

        return null;
    }

    public void onDestroy() throws IOException {
        if(in!=null) in.close();
        if(out!=null) out.close();
    }
}
