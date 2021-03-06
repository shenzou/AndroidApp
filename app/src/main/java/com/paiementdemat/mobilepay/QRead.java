package com.paiementdemat.mobilepay;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import me.dm7.barcodescanner.zxing.ZXingScannerView;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.auth0.android.jwt.Claim;
import com.auth0.android.jwt.JWT;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class QRead extends AppCompatActivity {

    ZXingScannerView qrCodeScanner;
    private final int REQUEST_PERMISSION_CAMERA=1;
    private NfcAdapter nfcAdapter;
    private TextView typeOfScan;
    //NFC
    public static final String MIME_TEXT_PLAIN = "text/plain";
    public static final String TAG = "NfcDemo";
    private Context context;
    public String backend_ip;
    public String result;
    public String transactionKey;

    ZXingScannerView.ResultHandler mResultHandler = new ZXingScannerView.ResultHandler() {
        @Override
        public void handleResult(Result result) {
            Context context = getApplicationContext();
            if(result != null){
                /*Toast toast = Toast.makeText(context, result.toString(), Toast.LENGTH_LONG);
                toast.show();*/
                handlePaymentResult(result.getText());
            }
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrread);
        Intent intent = getIntent();

        context = this;
        backend_ip = getString(R.string.backend_ip);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        typeOfScan = findViewById(R.id.typeOfScan);
        if (nfcAdapter == null) {
            typeOfScan.setText(R.string.ScanToPay);

        }
        else{
            if (!nfcAdapter.isEnabled()){
                typeOfScan.setText(R.string.ScanOrActivateToPay);

            } else{
                typeOfScan.setText(R.string.ScanOrApproachToPay);
                handleIntent(getIntent());
            }
        }
        qrCodeScanner = findViewById(R.id.qrCodeScanner);
        setScannerProperties();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    private Handler handler = new Handler();

    private Executor executor = new Executor() {
        @Override
        public void execute(Runnable command) {
            handler.post(command);
        }
    };

    private void showBiometricPrompt() {
        BiometricPrompt.PromptInfo promptInfo =
                new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.bioAuth))
                        .setSubtitle(getString(R.string.bioAuthDescription))
                        .setDeviceCredentialAllowed(true)
                        .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(QRead.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode,
                                              @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(),
                        "Authentication error: " + errString, Toast.LENGTH_SHORT)
                        .show();
            }

            @Override
            public void onAuthenticationSucceeded(
                    @NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                BiometricPrompt.CryptoObject authenticatedCryptoObject =
                        result.getCryptoObject();

                MakeTransaction();



                // User has verified the signature, cipher, or message
                // authentication code (MAC) associated with the crypto object,
                // so you can use it in your app's crypto-driven workflows.
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Authentication failed",
                        Toast.LENGTH_SHORT)
                        .show();
            }
        });

        // Displays the "log in" prompt.
        biometricPrompt.authenticate(promptInfo);
    }

    private void setScannerProperties() {
        List<BarcodeFormat> listBC = new ArrayList<>();
        listBC.add(BarcodeFormat.QR_CODE);

        qrCodeScanner.setFormats(listBC);
        qrCodeScanner.setAutoFocus(true);
        qrCodeScanner.setLaserColor(R.color.colorAccent);
        qrCodeScanner.setMaskColor(R.color.colorAccent);
        /*if (Build.MANUFACTURER.equals(HUAWEI, ignoreCase = true)) qrCodeScanner.setAspectTolerance(0.5f);*/

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSION_CAMERA);
            }
        }
        qrCodeScanner.startCamera();
        qrCodeScanner.setResultHandler(mResultHandler);
        setupForegroundDispatch(this, nfcAdapter);
    }

    @Override
    protected void onPause() {
        qrCodeScanner.stopCamera();
        stopForegroundDispatch(this, nfcAdapter);
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        /**
         * This method gets called, when a new Intent gets associated with the current activity instance.
         * Instead of creating a new activity, onNewIntent will be called. For more information have a look
         * at the documentation.
         *
         * In our case this method gets called, when the user attaches a Tag to the device.
         */
        handleIntent(intent);
    }

    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        if(adapter != null){
            final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

            final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);

            IntentFilter[] filters = new IntentFilter[1];
            String[][] techList = new String[][]{};

            // Notice that this is the same filter as in our manifest.
            filters[0] = new IntentFilter();
            filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
            filters[0].addCategory(Intent.CATEGORY_DEFAULT);
            try {
                filters[0].addDataType(MIME_TEXT_PLAIN);
            } catch (IntentFilter.MalformedMimeTypeException e) {
                throw new RuntimeException("Check your mime type.");
            }

            adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
        }
    }

    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        if(adapter != null){
            adapter.disableForegroundDispatch(activity);
        }

    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();

        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {

            String type = intent.getType();
            if (MIME_TEXT_PLAIN.equals(type)) {

                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

                new NdefReaderTask().execute(tag);

            } else {
                Log.d(TAG, "Wrong mime type: " + type);
            }
        } else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {

            // In case we would still use the Tech Discovered Intent
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            String[] techList = tag.getTechList();
            String searchedTech = Ndef.class.getName();

            for (String tech : techList) {
                if (searchedTech.equals(tech)) {
                    new NdefReaderTask().execute(tag);
                    break;
                }
            }
        }
    }

    private class NdefReaderTask extends AsyncTask<Tag, Void, String> {

        @Override
        protected String doInBackground(Tag... params) {

            Tag tag = params[0];

            Ndef ndef = Ndef.get(tag);
            if (ndef == null) {

                // NDEF is not supported by this Tag.
                return null;
            }

            NdefMessage ndefMessage = ndef.getCachedNdefMessage();

            NdefRecord[] records = ndefMessage.getRecords();
            for (NdefRecord ndefRecord : records) {

                String s = new String(ndefRecord.getType());

                String s2 = new String(NdefRecord.RTD_TEXT);

                if (ndefRecord.getTnf() == 2 && Arrays.equals(ndefRecord.getType(), "text/plain".getBytes())) {

                    try {

                        String read = readText(ndefRecord);

                        return read;
                    } catch (UnsupportedEncodingException e) {
                        Log.e(TAG, "Unsupported Encoding", e);
                    }
                }
            }

            return null;
        }

        private String readText(NdefRecord record) throws UnsupportedEncodingException {
            /*
             * See NFC forum specification for "Text Record Type Definition" at 3.2.1
             *
             * http://www.nfc-forum.org/specs/
             *
             * bit_7 defines encoding
             * bit_6 reserved for future use, must be 0
             * bit_5..0 length of IANA language code
             */

            byte[] payload = record.getPayload();

            // Get the Text Encoding
            String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";

            // Get the Language Code
            int languageCodeLength = payload[0] & 0063;

            // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
            // e.g. "en"

            // Get the Text
            String str = null;
            try{
                str = new String(payload, 0, payload.length, textEncoding);
            }
            catch(Exception e){
                Log.e(TAG, e.getMessage());
            }
            return str;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                handlePaymentResult(result);
            }
        }
    }

    private void handlePaymentResult(String result){
        //Toast.makeText(getApplicationContext(), result, Toast.LENGTH_LONG).show();
        AlertDialog.Builder dialog = new AlertDialog.Builder(context);
        JWT jwt = new JWT(result);
        Claim store = jwt.getClaim("commercant");
        Claim price = jwt.getClaim("amount");
        Claim transaction_id = jwt.getClaim("transaction_id");
        transactionKey = jwt.toString();

        String str = getString(R.string.stringAuthorizePayment).concat("\n".concat(getString(R.string.Store).concat(": ")).concat(store.asString()).concat("\n".concat(getString(R.string.price).concat(": "))).concat(price.asString()));
        dialog.setMessage(str);
        dialog.setTitle(R.string.Pay);
        dialog.setPositiveButton(R.string.Yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                BiometricManager biometricManager = BiometricManager.from(getApplicationContext());
                switch (biometricManager.canAuthenticate()) {
                    case BiometricManager.BIOMETRIC_SUCCESS:

                        SharedPreferences sharedPreferences = context.getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);
                        Boolean useBiometry = sharedPreferences.getBoolean("use_biometry", Boolean.TRUE);
                        if(useBiometry){
                            showBiometricPrompt();
                        } else{
                            MakeTransaction();
                        }

                        Log.d("Loop test", "Passed the loop");
                        break;
                    case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                        MakeTransaction();
                        break;
                    case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                        MakeTransaction();
                        break;
                    case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                        MakeTransaction();
                        break;
                }

            }
        });
        dialog.setNegativeButton(R.string.No, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        AlertDialog alertDialog = dialog.create();
        alertDialog.show();
        //typeOfScan.setText("Read content: " + result);
    }

    public void MakeTransaction(){
        try{
            SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);
            String userId = sharedPreferences.getString(getString(R.string.userID), null);
            if(userId == null) userId = getString(R.string.userID);
            String res = new ConfirmTransaction().execute(transactionKey, userId).get();
            JSONObject resultJSON = (JSONObject) new JSONTokener(res).nextValue();
            String status = resultJSON.getString("status");
            if(status.equals("success")){
                Toast.makeText(getApplicationContext(), "Paiement effectué",
                        Toast.LENGTH_LONG)
                        .show();
            }

        }
        catch(Exception e){
            Log.e("Error: ", e.getMessage());
        }
    }



    public class ConfirmTransaction extends AsyncTask<String, Void, String> {
        //RestTemplate restTemplate = new RestTemplate();

        @Override
        protected String doInBackground(String... strings) {
            JSONObject global = new JSONObject();
            JSONObject transaction = new JSONObject();

            try{
                transaction.put("transaction_key", strings[0]);
                transaction.put("payer_account", strings[1]);
                global.put("transaction", transaction);

                String addr = backend_ip + ":10001/transaction/pay";

                //JSONObject obj = new JSONObject("{ \"user\": { \"email\": \"dev2@app.com\", \"password\": \"admindev\", \"username\": \"flox27\", \"details\": { \"first_name\": \"florian\", \"last_name\": \"quibel\" } } }");
                Map<String, String> parameters = new HashMap<>();
                parameters.put("Content-Type", "application/json");

                SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);
                String apikey = sharedPreferences.getString(getString(R.string.api_token), null);
                if(apikey == null) apikey = getString(R.string.api_token);

                String token = "Bearer " + apikey;
                parameters.put("Authorization", token);

                Log.d("Token:", token);
                return RequestHandler.sendPostWithHeaders(addr, global, parameters);
            }
            catch(Exception e){
                return new String("Exception in Thread: " +e.getMessage());
            }


        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Log.d("Result: ", s);
            result = s;
        }
    }




}


